package com.school.people.app;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.school.academics.app.AcademicContext;
import com.school.academics.app.SchoolClassService;
import com.school.academics.domain.SchoolClass;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.id.IdGenerator;
import com.school.people.api.ImportError;
import com.school.people.api.ImportResult;
import com.school.people.api.StudentCreatedResponse;
import com.school.people.api.StudentRequest;
import com.school.people.domain.Gender;
import com.school.people.domain.Student;
import com.school.people.infra.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import static com.school.people.app.ImportColumns.Students;

/**
 * Admitting a spreadsheet full of students at once.
 *
 * <p>Two rules shape this class, and they are the reason it is as long as it is.
 *
 * <p><strong>Nothing is written until everything checks out.</strong> The file is validated end to
 * end first — required fields, dates, classes, sections, roll numbers, admission numbers — and only
 * then is a single student created. An admin uploading three hundred rows should never be left
 * guessing which half of them landed, and a half-imported class is far more work to undo than to
 * re-upload. The creation itself then runs in one transaction, so even a surprise at that stage
 * leaves the collection as it was.
 *
 * <p><strong>Every error in the file comes back at once.</strong> Validation does not stop at the
 * first bad cell: {@link ImportRow} records problems and carries on, so one upload yields the whole
 * list. Fixing a spreadsheet one error per round trip is the thing that makes bulk import useless.
 *
 * <p>Each row is then admitted through {@link StudentService#admit}, not by writing documents here.
 * Imported students must come out indistinguishable from typed-in ones — same ID series, same
 * login, same audit entry, same checks — and the way to guarantee that is to use the same code.
 */
@Service
public class StudentImportService {

	private static final String AUDIT_ENTITY = "StudentImport";

	/**
	 * Prefix for the admission numbers this import invents, e.g. {@code ADM-26-00001}. A school's own
	 * register numbers are optional in the template, because many schools do not have one for a
	 * student until the paperwork catches up, but the field is unique and non-null on the document.
	 */
	private static final String GENERATED_ADMISSION_PREFIX = "ADM";

	private static final Logger log = LoggerFactory.getLogger(StudentImportService.class);

	private final StudentService students;
	private final StudentRepository repository;
	private final SchoolClassService classes;
	private final AcademicContext academicContext;
	private final IdGenerator idGenerator;
	private final ImportCredentialsService credentialsFiles;
	private final AuditService audit;
	private final Clock clock;

	public StudentImportService(StudentService students, StudentRepository repository, SchoolClassService classes,
			AcademicContext academicContext, IdGenerator idGenerator, ImportCredentialsService credentialsFiles,
			AuditService audit, Clock clock) {
		this.students = students;
		this.repository = repository;
		this.classes = classes;
		this.academicContext = academicContext;
		this.idGenerator = idGenerator;
		this.credentialsFiles = credentialsFiles;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Validates a workbook and, if it is clean, admits every row in it.
	 *
	 * @return either the rejection with every bad cell in the file, or the count and the id of the
	 *         credentials file holding the temporary passwords
	 */
	@Transactional
	public ImportResult importStudents(MultipartFile file) {
		byte[] bytes = ImportSheet.bytesOf(file);
		// Read before anything else: it is the one thing that fails loudly rather than per-row, and
		// it is what an import of a file prepared before the session changed must be checked against.
		String sessionId = academicContext.currentSessionId();
		LocalDate today = LocalDate.now(clock);

		List<ImportError> errors = new ArrayList<>();
		List<Admission> admissions = new ArrayList<>();
		try (ImportSheet sheet = ImportSheet.open(bytes)) {
			List<ImportError> missing = sheet.missingColumns(Students.ALL);
			if (!missing.isEmpty()) {
				// No point reading rows out of a sheet whose shape we do not recognise.
				return ImportResult.rejected(missing);
			}
			Map<String, SchoolClass> byName = classes.list().stream()
					.collect(Collectors.toMap(schoolClass -> schoolClass.getName().toLowerCase(Locale.ROOT),
							Function.identity(), (first, second) -> first));
			for (ImportRow row : sheet.rows(errors)) {
				Admission admission = parse(row, byName, today);
				if (admission != null) {
					admissions.add(admission);
				}
			}
		}

		// Cross-row and cross-collection checks, which only make sense once every row has been read.
		// Skipped when a row already failed: the rows that did not parse are missing from the list, so
		// a duplicate check over what is left would report half a picture.
		Map<String, Set<Integer>> claimedRolls = new HashMap<>();
		if (errors.isEmpty()) {
			checkAdmissionNumbers(admissions);
			claimedRolls = checkRollNumbers(admissions, sessionId);
		}
		if (!errors.isEmpty()) {
			return ImportResult.rejected(errors);
		}

		return admitAll(admissions, sessionId, claimedRolls, today);
	}

	// --- validation -------------------------------------------------------------------------------

	/**
	 * Reads and checks one row.
	 *
	 * <p>Every accessor is called even after an earlier one has failed, so a row with four mistakes
	 * reports four. Only a row that came through clean is returned; the rest have already put their
	 * problems into the shared error list.
	 */
	private Admission parse(ImportRow row, Map<String, SchoolClass> byName, LocalDate today) {
		String name = row.required(Students.NAME, 120);
		LocalDate dob = row.pastDate(Students.DOB, today, true);
		Gender gender = row.requiredEnum(Students.GENDER, Gender.class);
		SchoolClass schoolClass = resolveClass(row, byName);
		String section = resolveSection(row, schoolClass);
		Integer rollNo = row.optionalInt(Students.ROLL_NO, 1, 9999);
		String admissionNo = row.optional(Students.ADMISSION_NO, 40);
		LocalDate admissionDate = row.optionalDate(Students.ADMISSION_DATE);

		StudentRequest.Guardians guardians = new StudentRequest.Guardians(
				row.optional(Students.FATHER_NAME, 120),
				row.optional(Students.MOTHER_NAME, 120),
				null,
				row.requiredPhone(Students.GUARDIAN_PHONE),
				row.optionalPhone(Students.ALTERNATE_PHONE),
				row.optionalEmail(Students.EMAIL),
				null);
		String addressLine = row.optional(Students.ADDRESS, 200);
		StudentRequest.Address address = addressLine == null
				? null
				: new StudentRequest.Address(addressLine, null, null, null, null);
		String bloodGroup = row.optional(Students.BLOOD_GROUP, 8);
		String previousSchool = row.optional(Students.PREVIOUS_SCHOOL, 160);

		if (!row.ok()) {
			return null;
		}
		return new Admission(row, name, dob, gender, schoolClass, section, rollNo, admissionNo,
				// A blank admission date means "today": a school importing its register on the day it
				// opens the system should not have to type the same date three hundred times.
				admissionDate == null ? today : admissionDate,
				guardians, address, bloodGroup, previousSchool);
	}

	private SchoolClass resolveClass(ImportRow row, Map<String, SchoolClass> byName) {
		String name = row.required(Students.SCHOOL_CLASS, 80);
		if (name == null) {
			return null;
		}
		SchoolClass schoolClass = byName.get(name.toLowerCase(Locale.ROOT));
		if (schoolClass == null) {
			row.error(Students.SCHOOL_CLASS, "there is no class named \"" + name
					+ "\" — use one of the names in the Class dropdown");
		}
		return schoolClass;
	}

	private String resolveSection(ImportRow row, SchoolClass schoolClass) {
		String section = row.required(Students.SECTION, 8);
		if (section == null || schoolClass == null) {
			return null;
		}
		String normalized = section.toUpperCase(Locale.ROOT);
		if (schoolClass.getSections() == null || !schoolClass.getSections().contains(normalized)) {
			row.error(Students.SECTION, schoolClass.getName() + " has no section " + normalized);
			return null;
		}
		return normalized;
	}

	/**
	 * Admission numbers must be unique across the school, so a number may be neither already on a
	 * student nor used twice within the file. Blank ones are skipped — one is generated for those.
	 *
	 * <p>The collection is asked once for the whole file rather than once per row.
	 */
	private void checkAdmissionNumbers(List<Admission> admissions) {
		List<String> given = admissions.stream()
				.map(Admission::admissionNo)
				.filter(Objects::nonNull)
				.toList();
		if (given.isEmpty()) {
			return;
		}
		Set<String> onFile = repository.findByAdmissionNoIn(given).stream()
				.map(Student::getAdmissionNo)
				.collect(Collectors.toSet());

		Set<String> seen = new HashSet<>();
		for (Admission admission : admissions) {
			String admissionNo = admission.admissionNo();
			if (admissionNo == null) {
				continue;
			}
			if (onFile.contains(admissionNo)) {
				admission.row().error(Students.ADMISSION_NO,
						"admission number " + admissionNo + " is already on another student");
			}
			else if (!seen.add(admissionNo)) {
				admission.row().error(Students.ADMISSION_NO,
						"admission number " + admissionNo + " appears more than once in this file");
			}
		}
	}

	/**
	 * Checks the roll numbers that were given, and returns what each section now has claimed, so the
	 * blank ones can be filled in afterwards without colliding with them.
	 *
	 * @return claimed roll numbers per {@code classId|section}; blank entries for sections that only
	 *         have auto-numbered rows
	 */
	private Map<String, Set<Integer>> checkRollNumbers(List<Admission> admissions, String sessionId) {
		Map<String, Set<Integer>> claimed = new HashMap<>();
		for (Admission admission : admissions) {
			Set<Integer> inSection = claimed.computeIfAbsent(admission.sectionKey(), key -> new HashSet<>());
			Integer rollNo = admission.rollNo();
			if (rollNo == null) {
				continue;
			}
			if (repository.existsByEnrollmentSessionIdAndEnrollmentClassIdAndEnrollmentSectionAndEnrollmentRollNo(
					sessionId, admission.schoolClass().getId(), admission.section(), rollNo)) {
				admission.row().error(Students.ROLL_NO, "roll number " + rollNo + " is already used in "
						+ admission.sectionName() + " this session");
			}
			else if (!inSection.add(rollNo)) {
				admission.row().error(Students.ROLL_NO, "roll number " + rollNo + " appears more than once for "
						+ admission.sectionName() + " in this file");
			}
		}
		return claimed;
	}

	// --- writing ----------------------------------------------------------------------------------

	/**
	 * Admits every row, numbering the ones that came without a roll number, and stores the credentials
	 * the admissions produced.
	 *
	 * <p>The credentials file is written last, after the students exist. It is stored through the
	 * object store rather than the database, so it is not part of this transaction: if the commit
	 * were to fail after it is written, the file is orphaned rather than wrong, and the retention
	 * sweep removes it within the day.
	 */
	private ImportResult admitAll(List<Admission> admissions, String sessionId,
			Map<String, Set<Integer>> claimedRolls, LocalDate today) {
		Map<String, Integer> nextRollNo = new HashMap<>();
		List<ImportCredentialsService.Credential> credentials = new ArrayList<>(admissions.size());

		for (Admission admission : admissions) {
			int rollNo = admission.rollNo() != null
					? admission.rollNo()
					: assignRollNo(admission, sessionId, claimedRolls, nextRollNo);
			StudentCreatedResponse created = students.admit(requestFor(admission, rollNo));
			credentials.add(new ImportCredentialsService.Credential(admission.name(), admission.sectionName(),
					created.uniqueId(), created.temporaryPassword()));
		}

		String credentialsFileId = credentialsFiles.store(credentials);
		// The count and the file id only: the passwords themselves are never audited, and never logged.
		audit.record(AuditAction.STUDENTS_IMPORTED, AUDIT_ENTITY, credentialsFileId, null,
				Map.of("created", credentials.size(), "importedOn", today.toString()));
		log.info("Imported {} student(s) from a spreadsheet", credentials.size());
		return ImportResult.created(credentials.size(), credentialsFileId);
	}

	/**
	 * The next free roll number in this student's section: one past the highest the section already
	 * has, then past anything this file claimed explicitly.
	 */
	private int assignRollNo(Admission admission, String sessionId, Map<String, Set<Integer>> claimedRolls,
			Map<String, Integer> nextRollNo) {
		String key = admission.sectionKey();
		int candidate = nextRollNo.computeIfAbsent(key, k -> highestRollNo(sessionId, admission) + 1);
		Set<Integer> claimed = claimedRolls.computeIfAbsent(key, k -> new HashSet<>());
		while (!claimed.add(candidate)) {
			candidate++;
		}
		nextRollNo.put(key, candidate + 1);
		return candidate;
	}

	/** The highest roll number in the section, whatever the holder's status, or 0 if nobody is in it. */
	private int highestRollNo(String sessionId, Admission admission) {
		return repository
				.findFirstByEnrollmentSessionIdAndEnrollmentClassIdAndEnrollmentSectionOrderByEnrollmentRollNoDesc(
						sessionId, admission.schoolClass().getId(), admission.section())
				.map(student -> student.getEnrollment() == null ? 0 : student.getEnrollment().rollNo())
				.orElse(0);
	}

	private StudentRequest requestFor(Admission admission, int rollNo) {
		return new StudentRequest(
				admission.name(),
				admission.dob(),
				admission.gender(),
				null,
				admission.admissionNo() != null
						? admission.admissionNo()
						: idGenerator.nextNumber(GENERATED_ADMISSION_PREFIX, 5),
				admission.admissionDate(),
				new StudentRequest.Enrollment(admission.schoolClass().getId(), admission.section(), rollNo),
				admission.guardians(),
				admission.address(),
				admission.bloodGroup(),
				admission.previousSchool());
	}

	/**
	 * One validated row, held until the whole file has been checked.
	 *
	 * @param row       the row it came from, so a cross-row check found later can still report against
	 *                  the right line of the spreadsheet
	 * @param rollNo    null when the cell was blank, which means "give them the next free number"
	 */
	private record Admission(ImportRow row, String name, LocalDate dob, Gender gender, SchoolClass schoolClass,
			String section, Integer rollNo, String admissionNo, LocalDate admissionDate,
			StudentRequest.Guardians guardians, StudentRequest.Address address, String bloodGroup,
			String previousSchool) {

		/** Identity of the section within this import; roll numbers are unique inside one of these. */
		String sectionKey() {
			return schoolClass.getId() + "|" + section;
		}

		/** How the section is written in messages and on the credentials sheet, e.g. {@code Class 1-A}. */
		String sectionName() {
			return schoolClass.getName() + "-" + section;
		}
	}
}

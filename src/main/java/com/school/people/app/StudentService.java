package com.school.people.app;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.school.academics.app.AcademicContext;
import com.school.academics.app.SchoolClassService;
import com.school.academics.domain.AcademicSession;
import com.school.academics.domain.SchoolClass;
import com.school.auth.app.NewUser;
import com.school.auth.app.UserService;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ForbiddenException;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ValidationException;
import com.school.common.fees.FeeStatus;
import com.school.common.fees.StudentFeeStatusProvider;
import com.school.common.id.IdGenerator;
import com.school.common.id.IdType;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.CurrentUser;
import com.school.common.security.Permission;
import com.school.common.security.Role;
import com.school.common.security.TemporaryPasswordGenerator;
import com.school.people.api.StudentAdminView;
import com.school.people.api.StudentCreatedResponse;
import com.school.people.api.StudentListItem;
import com.school.people.api.StudentRequest;
import com.school.people.api.StudentSelfView;
import com.school.people.api.StudentStatusRequest;
import com.school.people.api.StudentTeacherView;
import com.school.people.api.StudentView;
import com.school.people.domain.Address;
import com.school.people.domain.Enrollment;
import com.school.people.domain.Guardians;
import com.school.people.domain.Student;
import com.school.people.domain.StudentStatus;
import com.school.people.infra.StudentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Students: admission, lookup, editing, status and password resets.
 *
 * <p>Two rules shape this class. <strong>Who may see what</strong> is decided here, from the
 * security context, and returned as one of three unrelated record types rather than as one record
 * with fields blanked out. <strong>Who may see whom</strong> — a student reading only their own
 * record — is an object-level check, which {@code @PreAuthorize} cannot express, so it lives in
 * {@link #view(String)}.
 *
 * <p>Classes, sections and the current session are read through the academics module's public
 * services; fee status through the fees module's. Neither module's repositories are touched.
 */
@Service
public class StudentService {

	private static final String AUDIT_ENTITY = "Student";

	private final StudentRepository students;
	private final MongoOperations mongo;
	private final SchoolClassService classes;
	private final AcademicContext academicContext;
	private final UserService users;
	private final StudentFeeStatusProvider feeStatuses;
	private final IdGenerator idGenerator;
	private final TemporaryPasswordGenerator temporaryPasswords;
	private final AuditService audit;
	private final Clock clock;

	public StudentService(StudentRepository students, MongoOperations mongo, SchoolClassService classes,
			AcademicContext academicContext, UserService users, StudentFeeStatusProvider feeStatuses,
			IdGenerator idGenerator, TemporaryPasswordGenerator temporaryPasswords, AuditService audit, Clock clock) {
		this.students = students;
		this.mongo = mongo;
		this.classes = classes;
		this.academicContext = academicContext;
		this.users = users;
		this.feeStatuses = feeStatuses;
		this.idGenerator = idGenerator;
		this.temporaryPasswords = temporaryPasswords;
		this.audit = audit;
		this.clock = clock;
	}

	// --- admission --------------------------------------------------------------------------------

	/**
	 * Admits a student: issues the STU id, writes the student, and creates the login that shares that
	 * id, all in one transaction. Either both documents exist afterwards or neither does — a student
	 * who cannot log in, or a login with no record behind it, is worse than a failed request.
	 *
	 * <p>The counter increment joins the same transaction, so a rollback hands the number back and the
	 * next admission reuses it. That is harmless — nothing was written under it — and it is the reason
	 * MongoDB has to run as a replica set even locally.
	 *
	 * @return the student together with the temporary password, which is shown exactly once
	 */
	@Transactional
	public StudentCreatedResponse admit(StudentRequest request) {
		AcademicSession session = academicContext.currentSession();
		SchoolClass schoolClass = validatedClass(request.enrollment());
		String section = normalizeSection(request.enrollment().section());
		String admissionNo = request.admissionNo().trim();

		if (students.existsByAdmissionNo(admissionNo)) {
			throw new ConflictException("Admission number " + admissionNo + " is already on another student");
		}
		requireFreeRollNumber(session.getId(), schoolClass, section, request.enrollment().rollNo(), null);

		String uniqueId = idGenerator.next(IdType.STU);
		Instant now = Instant.now(clock);
		Student student = students.insert(Student.builder()
				.uniqueId(uniqueId)
				.name(request.name().trim())
				.dob(request.dob())
				.gender(request.gender())
				.photoUrl(request.photoUrl())
				.admissionNo(admissionNo)
				.admissionDate(request.admissionDate())
				.enrollment(new Enrollment(session.getId(), schoolClass.getId(), section,
						request.enrollment().rollNo()))
				.guardians(toGuardians(request.guardians()))
				.address(toAddress(request.address()))
				.bloodGroup(request.bloodGroup())
				.previousSchool(request.previousSchool())
				.status(StudentStatus.ACTIVE)
				.createdAt(now)
				.updatedAt(now)
				.build());

		String temporaryPassword = temporaryPasswords.generate();
		// No e-mail on the login: students log in with their uniqueId, and siblings routinely share
		// the family address, which the sparse unique index on users.email would reject.
		users.createWithUniqueId(uniqueId, new NewUser(Role.STUDENT, student.getName(), null, temporaryPassword,
				true, student.getId()));

		audit.record(AuditAction.STUDENT_CREATED, AUDIT_ENTITY, uniqueId, null, student);
		return new StudentCreatedResponse(uniqueId, temporaryPassword,
				StudentAdminView.of(student, schoolClass.getName(), feeStatuses.statusFor(uniqueId)));
	}

	// --- reading ----------------------------------------------------------------------------------

	/**
	 * One student, projected for whoever is asking.
	 *
	 * <p>An admin sees everything, a teacher the teacher view, a student only themselves. A student
	 * asking for somebody else gets 403 rather than 404: the ID is in the URL they typed, so denying
	 * its existence would be theatre, and 403 is the honest answer.
	 */
	public StudentView view(String uniqueId) {
		AuthPrincipal caller = CurrentUser.require();
		Student student = require(uniqueId);
		String className = classNameOf(student);
		FeeStatus feeStatus = feeStatuses.statusFor(student.getUniqueId());

		if (caller.permissions().contains(Permission.STUDENT_READ_FULL)) {
			return StudentAdminView.of(student, className, feeStatus);
		}
		if (isSelf(caller, student)) {
			return StudentSelfView.of(student, className, feeStatus);
		}
		if (caller.permissions().contains(Permission.STUDENT_READ_BASIC)) {
			return StudentTeacherView.of(student, className, feeStatus);
		}
		throw new ForbiddenException("You may only read your own student record");
	}

	/** {@code GET /students/me}: the caller's own record. */
	public StudentSelfView viewSelf() {
		AuthPrincipal caller = CurrentUser.require();
		Student student = students.findByUniqueId(caller.uniqueId())
				.orElseThrow(() -> new NotFoundException(
						"This login has no student record. Only students have one."));
		return StudentSelfView.of(student, classNameOf(student), feeStatuses.statusFor(student.getUniqueId()));
	}

	/**
	 * The filtered, paged student list.
	 *
	 * <p>{@code q} is matched as an anchored prefix against three fields, so every branch can use an
	 * index; an unanchored {@code /substring/} would force a collection scan on every keystroke. The
	 * text is quoted before it reaches the regex, so a caller cannot inject one.
	 *
	 * <p>{@code feeStatus} is the one filter that cannot be a criterion: it is derived from the fees
	 * module's invoices, not stored on the student. It is resolved in two steps — narrow by the stored
	 * filters, ask fees for the statuses of exactly those students, then page over the ones that
	 * match. Filtering the page after fetching it would be cheaper but would make {@code totalItems}
	 * and the page boundaries lie.
	 */
	public Page<Student> search(StudentSearch search, Pageable pageable) {
		Query query = new Query();
		List<Criteria> criteria = new ArrayList<>();
		if (hasText(search.classId())) {
			criteria.add(Criteria.where("enrollment.classId").is(search.classId().trim()));
		}
		if (hasText(search.section())) {
			criteria.add(Criteria.where("enrollment.section").is(normalizeSection(search.section())));
		}
		if (search.status() != null) {
			criteria.add(Criteria.where("status").is(search.status()));
		}
		if (hasText(search.q())) {
			String term = search.q().trim();
			String prefix = "^" + Pattern.quote(term);
			criteria.add(new Criteria().orOperator(
					Criteria.where("name").regex(prefix, "i"),
					Criteria.where("uniqueId").regex("^" + Pattern.quote(term.toUpperCase(Locale.ROOT))),
					Criteria.where("guardians.phone").regex(prefix)));
		}
		if (search.feeStatus() != null) {
			// Computed from the criteria gathered so far, so fees is only asked about the students the
			// stored filters already allow.
			List<String> matching = uniqueIdsWithFeeStatus(criteria, search.feeStatus());
			criteria.add(Criteria.where("uniqueId").in(matching));
		}
		if (!criteria.isEmpty()) {
			query.addCriteria(new Criteria().andOperator(criteria));
		}

		long total = mongo.count(query, Student.class);
		// Name order unless the caller asked for something else; an unsorted Mongo query has no
		// defined order at all, so page 2 could otherwise repeat a row from page 1.
		Pageable effective = pageable.getSort().isSorted()
				? pageable
				: PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name"));
		List<Student> items = mongo.find(query.with(effective), Student.class);
		return new PageImpl<>(items, effective, total);
	}

	/**
	 * The {@code uniqueId}s, among those the stored filters allow, that stand this way on fees.
	 *
	 * <p>Only the {@code uniqueId} is projected, so this is an index-covered read of a key per student
	 * rather than the documents themselves, and fees answers for all of them in one query.
	 *
	 * @param narrowing the criteria built from the stored filters; not modified
	 */
	private List<String> uniqueIdsWithFeeStatus(List<Criteria> narrowing, FeeStatus wanted) {
		Query query = new Query();
		if (!narrowing.isEmpty()) {
			query.addCriteria(new Criteria().andOperator(List.copyOf(narrowing)));
		}
		query.fields().include("uniqueId");
		List<String> candidates = mongo.find(query, Student.class).stream().map(Student::getUniqueId).toList();
		if (candidates.isEmpty()) {
			return List.of();
		}
		Map<String, FeeStatus> statuses = feeStatuses.statusesFor(candidates);
		return candidates.stream().filter(uniqueId -> statuses.get(uniqueId) == wanted).toList();
	}

	/**
	 * Projects a page of students to list rows, resolving class names and fee statuses in bulk rather
	 * than once per row.
	 */
	public List<StudentListItem> toListItems(List<Student> page) {
		if (page.isEmpty()) {
			return List.of();
		}
		Map<String, String> classNames = classes.list().stream()
				.collect(Collectors.toMap(SchoolClass::getId, SchoolClass::getName));
		Map<String, FeeStatus> statuses = feeStatuses.statusesFor(page.stream().map(Student::getUniqueId).toList());
		return page.stream()
				.map(student -> StudentListItem.of(student,
						student.getEnrollment() == null ? null : classNames.get(student.getEnrollment().classId()),
						statuses.get(student.getUniqueId())))
				.toList();
	}

	// --- editing ----------------------------------------------------------------------------------

	/**
	 * Replaces the editable details. The {@code uniqueId} and the status are untouched — the first can
	 * never change, and the second has its own endpoint.
	 */
	@Transactional
	public StudentAdminView update(String uniqueId, StudentRequest request) {
		Student before = require(uniqueId);
		AcademicSession session = academicContext.currentSession();
		SchoolClass schoolClass = validatedClass(request.enrollment());
		String section = normalizeSection(request.enrollment().section());
		String admissionNo = request.admissionNo().trim();

		if (students.existsByAdmissionNoAndIdNot(admissionNo, before.getId())) {
			throw new ConflictException("Admission number " + admissionNo + " is already on another student");
		}
		requireFreeRollNumber(session.getId(), schoolClass, section, request.enrollment().rollNo(), before.getId());

		String name = request.name().trim();
		Student saved = students.save(before.toBuilder()
				.name(name)
				.dob(request.dob())
				.gender(request.gender())
				.photoUrl(request.photoUrl())
				.admissionNo(admissionNo)
				.admissionDate(request.admissionDate())
				.enrollment(new Enrollment(session.getId(), schoolClass.getId(), section,
						request.enrollment().rollNo()))
				.guardians(toGuardians(request.guardians()))
				.address(toAddress(request.address()))
				.bloodGroup(request.bloodGroup())
				.previousSchool(request.previousSchool())
				.updatedAt(Instant.now(clock))
				.build());

		// The display name in the token and in /auth/me comes from the login, not from here.
		if (!name.equals(before.getName())) {
			users.rename(before.getUniqueId(), name);
		}
		audit.record(AuditAction.STUDENT_UPDATED, AUDIT_ENTITY, before.getUniqueId(), before, saved);
		return StudentAdminView.of(saved, schoolClass.getName(), feeStatuses.statusFor(saved.getUniqueId()));
	}

	/** Moves a student between ACTIVE, LEFT and ALUMNI. A no-op change is still recorded as one. */
	public StudentAdminView changeStatus(String uniqueId, StudentStatusRequest request) {
		Student before = require(uniqueId);
		Student saved = students.save(before.toBuilder()
				.status(request.status())
				.updatedAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.STUDENT_STATUS_CHANGED, AUDIT_ENTITY, before.getUniqueId(), before, saved);
		return StudentAdminView.of(saved, classNameOf(saved), feeStatuses.statusFor(saved.getUniqueId()));
	}

	/**
	 * Issues a fresh temporary password for a student's login. The student document is untouched; the
	 * work and the audit entry both belong to the auth module, which owns credentials.
	 *
	 * @return the plain password, shown once
	 */
	public String resetPassword(String uniqueId) {
		Student student = require(uniqueId);
		return users.resetTemporaryPassword(student.getUniqueId());
	}

	// --- internals --------------------------------------------------------------------------------

	// --- for other modules ------------------------------------------------------------------------

	/**
	 * The ACTIVE students of one class-section, in roll order. This is the register attendance (B8)
	 * marks and exams (B9) enter marks against.
	 *
	 * <p>{@code LEFT} and {@code ALUMNI} students are left out: a register is of who is here now, and
	 * the historical records of those who have gone are reached through the student, not the class.
	 */
	public List<StudentRef> activeInSection(String classId, String section) {
		return students.findByEnrollmentClassIdAndEnrollmentSectionAndStatusOrderByEnrollmentRollNoAsc(
						classId, normalizeSection(section), StudentStatus.ACTIVE).stream()
				.map(StudentService::toRef)
				.toList();
	}

	/**
	 * The ACTIVE students of one class in one session, across every section, in section-then-roll
	 * order. This is who gets billed when fees (B11) generates invoices for a class.
	 *
	 * <p>Filtered on the enrollment's session as well as the class, so generating this year's invoices
	 * cannot pick up a student whose record still sits in last year's roll.
	 */
	public List<StudentRef> activeInClass(String sessionId, String classId) {
		return students
				.findByEnrollmentSessionIdAndEnrollmentClassIdAndStatusOrderByEnrollmentSectionAscEnrollmentRollNoAsc(
						sessionId, classId, StudentStatus.ACTIVE).stream()
				.map(StudentService::toRef)
				.toList();
	}

	/** Names one student for another module, without handing over the document. */
	public StudentRef requireRef(String uniqueId) {
		return toRef(require(uniqueId));
	}

	/**
	 * The same, but empty rather than 404 when there is no such student — for callers asking "is this
	 * login a student, and if so which class?", where not being one is a normal answer.
	 */
	public Optional<StudentRef> findRef(String uniqueId) {
		return students.findByUniqueId(UserService.normalizeUniqueId(uniqueId)).map(StudentService::toRef);
	}

	/**
	 * The family's contact details, for pre-filling a payment checkout (B12).
	 *
	 * <p>A deliberately narrow second projection: a name, an email and a phone number, and none of the
	 * guardian names, address or admission history that sit beside them on the document.
	 */
	public StudentContact contactFor(String uniqueId) {
		Student student = require(uniqueId);
		Guardians guardians = student.getGuardians();
		return new StudentContact(student.getUniqueId(), student.getName(),
				guardians == null ? null : guardians.email(),
				guardians == null ? null : guardians.phone());
	}

	private static StudentRef toRef(Student student) {
		Enrollment enrollment = student.getEnrollment();
		return new StudentRef(student.getUniqueId(), student.getName(),
				enrollment == null ? null : enrollment.classId(),
				enrollment == null ? null : enrollment.section(),
				enrollment == null ? 0 : enrollment.rollNo());
	}

	/** The student document, for other services in this module. */
	public Student require(String uniqueId) {
		String normalized = UserService.normalizeUniqueId(uniqueId);
		return students.findByUniqueId(normalized)
				.orElseThrow(() -> NotFoundException.of("Student", normalized));
	}

	private static boolean isSelf(AuthPrincipal caller, Student student) {
		return caller.uniqueId() != null && caller.uniqueId().equals(student.getUniqueId());
	}

	/** The class must exist and must actually have the section being asked for. */
	private SchoolClass validatedClass(StudentRequest.Enrollment enrollment) {
		SchoolClass schoolClass;
		try {
			schoolClass = classes.get(enrollment.classId());
		}
		catch (NotFoundException ex) {
			throw new ValidationException("The enrollment does not point at a real class",
					List.of(new FieldViolation("enrollment.classId",
							"no class with id " + enrollment.classId())));
		}
		String section = normalizeSection(enrollment.section());
		if (schoolClass.getSections() == null || !schoolClass.getSections().contains(section)) {
			throw new ValidationException("The enrollment does not point at a real section",
					List.of(new FieldViolation("enrollment.section",
							schoolClass.getName() + " has no section " + section)));
		}
		return schoolClass;
	}

	/**
	 * Roll numbers identify a student within a section on every mark sheet and attendance register, so
	 * two students cannot share one.
	 *
	 * @param excludeId the student being edited, so an update that keeps its own roll number passes
	 */
	private void requireFreeRollNumber(String sessionId, SchoolClass schoolClass, String section, int rollNo,
			String excludeId) {
		boolean taken = excludeId == null
				? students.existsByEnrollmentSessionIdAndEnrollmentClassIdAndEnrollmentSectionAndEnrollmentRollNo(
						sessionId, schoolClass.getId(), section, rollNo)
				: students
						.existsByEnrollmentSessionIdAndEnrollmentClassIdAndEnrollmentSectionAndEnrollmentRollNoAndIdNot(
								sessionId, schoolClass.getId(), section, rollNo, excludeId);
		if (taken) {
			throw new ConflictException("Roll number " + rollNo + " is already used in "
					+ schoolClass.getName() + "-" + section + " this session");
		}
	}

	/** Null when the student has no enrollment, or when the class behind it has since been deleted. */
	private String classNameOf(Student student) {
		if (student.getEnrollment() == null || student.getEnrollment().classId() == null) {
			return null;
		}
		return classes.findById(student.getEnrollment().classId()).map(SchoolClass::getName).orElse(null);
	}

	private static String normalizeSection(String section) {
		return section == null ? null : section.trim().toUpperCase(Locale.ROOT);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static Guardians toGuardians(StudentRequest.Guardians request) {
		return new Guardians(trim(request.fatherName()), trim(request.motherName()), trim(request.guardianName()),
				trim(request.phone()), trim(request.altPhone()), lower(request.email()), trim(request.occupation()));
	}

	private static Address toAddress(StudentRequest.Address request) {
		return request == null ? null : new Address(trim(request.line1()), trim(request.line2()),
				trim(request.city()), trim(request.state()), trim(request.postalCode()));
	}

	private static String trim(String value) {
		return map(value, String::trim);
	}

	private static String lower(String value) {
		return map(value, text -> text.trim().toLowerCase(Locale.ROOT));
	}

	/** Blank becomes null, so an empty form field is stored as "absent" rather than as "". */
	private static String map(String value, Function<String, String> mapper) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return mapper.apply(value);
	}
}

package com.school.people.app;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.school.academics.app.SubjectService;
import com.school.academics.domain.Subject;
import com.school.auth.app.UserService;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.people.api.EmployeeRequest;
import com.school.people.api.EmployeeSavedResponse;
import com.school.people.api.ImportError;
import com.school.people.api.ImportResult;
import com.school.people.domain.EmployeeType;
import com.school.people.domain.Gender;
import com.school.people.domain.ImportJob;
import com.school.people.domain.ImportKind;
import com.school.people.infra.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import static com.school.people.app.ImportColumns.Employees;

/**
 * Taking on a spreadsheet full of teachers and staff at once.
 *
 * <p>The counterpart to {@link StudentImportService} and built the same way: the whole file is
 * validated before a single employee is written, every error in it comes back together, and the rows
 * that pass are hired through {@link EmployeeService#hire} so an imported employee is
 * indistinguishable from a typed-in one.
 *
 * <p>It also shares the same two-phase write, and for the same reason. Every EMP id, password and
 * BCrypt hash is produced before any transaction opens, and each row is then written in a short
 * transaction of its own — one transaction around the whole loop cannot survive Atlas's sixty-second
 * limit once a file has a couple of hundred rows in it. A row that fails stops the import and is
 * reported as {@code failedAtRow}; re-uploading the same file resumes from there.
 *
 * <p>What differs from students is the shape of a row. The {@code Type} column decides which half of
 * it applies — {@code Qualification} and {@code Subjects} for a teacher, {@code Designation} and
 * {@code Can Login} for a staff member — and the other half is <em>ignored</em> rather than rejected,
 * exactly as {@code PUT /employees/{uniqueId}} ignores it. A spreadsheet that has had both kinds of
 * column filled in for everybody is a perfectly normal thing to receive.
 *
 * <p>Not everyone imported gets a login: a staff member with {@code Can Login = NO} does not, and so
 * has no password hashed for them and no line on the credentials sheet. A file of nothing but those
 * produces no credentials file at all.
 */
@Service
public class EmployeeImportService {

	private static final String AUDIT_ENTITY = "EmployeeImport";

	private static final Logger log = LoggerFactory.getLogger(EmployeeImportService.class);

	private final EmployeeService employees;
	private final EmployeeRepository repository;
	private final SubjectService subjects;
	private final UserService users;
	private final ImportCredentialsService credentialsFiles;
	private final ImportJobService jobs;
	private final AuditService audit;
	private final Clock clock;

	public EmployeeImportService(EmployeeService employees, EmployeeRepository repository, SubjectService subjects,
			UserService users, ImportCredentialsService credentialsFiles, ImportJobService jobs, AuditService audit,
			Clock clock) {
		this.employees = employees;
		this.repository = repository;
		this.subjects = subjects;
		this.users = users;
		this.credentialsFiles = credentialsFiles;
		this.jobs = jobs;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Validates a workbook and, if it is clean, hires every row in it.
	 *
	 * <p>Not {@code @Transactional} on purpose; the transactions are inside, one per row.
	 *
	 * @return the rejection with every bad cell in the file; or the count and the id of the credentials
	 *         file — which is null when no row produced a login; or, if a row failed to write, how far
	 *         it got
	 */
	public ImportResult importEmployees(MultipartFile file) {
		long startedNanos = System.nanoTime();
		byte[] bytes = ImportSheet.bytesOf(file);
		LocalDate today = LocalDate.now(clock);

		List<ImportError> errors = new ArrayList<>();
		List<Hire> parsed = new ArrayList<>();
		try (ImportSheet sheet = ImportSheet.open(bytes)) {
			List<ImportError> missing = sheet.missingColumns(Employees.ALL);
			if (!missing.isEmpty()) {
				return ImportResult.rejected(missing);
			}
			Map<String, String> subjectIdsByName = subjects.list().stream()
					.collect(Collectors.toMap(subject -> subject.getName().toLowerCase(Locale.ROOT),
							Subject::getId, (first, second) -> first));
			for (ImportRow row : sheet.rows(errors)) {
				Hire hire = parse(row, subjectIdsByName, today);
				if (hire != null) {
					parsed.add(hire);
				}
			}
		}

		// The same file as a run that stopped partway? Then the rows it already wrote are skipped
		// instead of being hired a second time.
		String jobKey = ImportJobService.keyFor(ImportKind.EMPLOYEES, bytes);
		ImportJob resuming = errors.isEmpty() ? jobs.resumable(jobKey).orElse(null) : null;
		List<Hire> hires = resuming == null ? parsed : remaining(parsed, resuming);
		Run run = new Run(jobKey, resuming, parsed.size(), parsed.size() - hires.size(), startedNanos);

		if (errors.isEmpty()) {
			checkEmails(hires);
		}
		if (!errors.isEmpty()) {
			return ImportResult.rejected(errors);
		}

		return hireAll(run, hires, today);
	}

	// --- validation -------------------------------------------------------------------------------

	/** Reads and checks one row, calling every accessor so that one pass reports every mistake in it. */
	private Hire parse(ImportRow row, Map<String, String> subjectIdsByName, LocalDate today) {
		EmployeeType type = row.requiredEnum(Employees.TYPE, EmployeeType.class);
		boolean teacher = type == EmployeeType.TEACHER;

		String name = row.required(Employees.NAME, 120);
		LocalDate dob = row.pastDate(Employees.DOB, today, false);
		Gender gender = row.optionalEnum(Employees.GENDER, Gender.class);
		String phone = row.requiredPhone(Employees.PHONE);
		String email = row.optionalEmail(Employees.EMAIL);
		LocalDate joiningDate = row.requiredDate(Employees.JOINING_DATE);

		// Read whatever the type does not use as well, so a cell over the length limit is still
		// reported rather than silently thrown away. The values themselves are then dropped.
		String designation = row.optional(Employees.DESIGNATION, 80);
		String qualification = row.optional(Employees.QUALIFICATION, 200);
		List<String> subjectIds = resolveSubjects(row, subjectIdsByName, teacher);
		Boolean canLogin = row.optionalYesNo(Employees.CAN_LOGIN);
		EmployeeRequest.Bank bank = resolveBank(row);

		if (!row.ok()) {
			return null;
		}
		return new Hire(row, new EmployeeRequest(
				type,
				name,
				dob,
				gender,
				phone,
				email,
				null,
				joiningDate,
				null,
				bank,
				null,
				teacher ? qualification : null,
				teacher ? subjectIds : null,
				null,
				teacher ? null : designation,
				// A teacher always gets a login; for staff the column decides, and a blank means no.
				teacher || Boolean.TRUE.equals(canLogin),
				// The spreadsheet has no column for it: an import creates plain staff logins, and an
				// accountant is appointed afterwards through PUT /employees/{uniqueId}. A column would
				// hand out fee and accounts permissions to whoever filled the file in.
				null));
	}

	/**
	 * The subject ids behind a comma-separated list of subject names.
	 *
	 * <p>Names rather than ids, because nobody is going to paste Mongo ObjectIds into a spreadsheet.
	 * A name that is not a subject is an error rather than a new subject: inventing one from a typo
	 * would put a misspelling on report cards for the rest of the year.
	 *
	 * @param teacher false for a staff row, where the column is ignored — but still read, so an
	 *                employee later changed to a teacher does not carry an unchecked list
	 */
	private List<String> resolveSubjects(ImportRow row, Map<String, String> subjectIdsByName, boolean teacher) {
		String value = row.optional(Employees.SUBJECTS, 400);
		if (value == null) {
			return null;
		}
		Set<String> ids = new LinkedHashSet<>();
		for (String name : Arrays.stream(value.split(",")).map(String::trim).filter(part -> !part.isEmpty()).toList()) {
			String id = subjectIdsByName.get(name.toLowerCase(Locale.ROOT));
			if (id == null) {
				if (teacher) {
					row.error(Employees.SUBJECTS, "there is no subject named \"" + name
							+ "\" — the subject names are listed on the Instructions sheet");
				}
			}
			else {
				ids.add(id);
			}
		}
		return ids.isEmpty() ? null : List.copyOf(ids);
	}

	/**
	 * The bank block, or null when none of its columns were filled in.
	 *
	 * <p>A row with a bank name and an IFSC but no account number is a mistake rather than an empty
	 * block, and silently dropping it would leave payroll unable to pay somebody who looks set up.
	 */
	private EmployeeRequest.Bank resolveBank(ImportRow row) {
		String accountName = row.optional(Employees.BANK_ACCOUNT_NAME, 120);
		String accountNumber = row.optionalDigits(Employees.ACCOUNT_NUMBER, 6, 20);
		String ifsc = row.optional(Employees.IFSC, 20);
		String bankName = row.optional(Employees.BANK_NAME, 120);

		if (accountNumber == null) {
			if (accountName != null || ifsc != null || bankName != null) {
				row.error(Employees.ACCOUNT_NUMBER, "is required when any other bank column is filled in");
			}
			return null;
		}
		return new EmployeeRequest.Bank(accountName, accountNumber, ifsc, bankName);
	}

	/**
	 * An employee's e-mail address is their login's, so two of them cannot share one.
	 *
	 * <p>Checked here rather than left to the unique index, because the index would fail a row halfway
	 * through the file and the admin would get one address named instead of a list of the rows to fix.
	 * Now that each row commits on its own, that matters more than it did: a clash found at write time
	 * stops the import with rows already on file behind it.
	 */
	private void checkEmails(List<Hire> hires) {
		Set<String> seen = new HashSet<>();
		for (Hire hire : hires) {
			String email = hire.request().email();
			if (email == null) {
				continue;
			}
			if (!seen.add(email)) {
				hire.row().error(Employees.EMAIL, email + " appears more than once in this file");
			}
			else if (users.emailTaken(email)) {
				hire.row().error(Employees.EMAIL, email + " is already somebody's login");
			}
		}
	}

	// --- resuming ---------------------------------------------------------------------------------

	/**
	 * The rows of a re-uploaded file that still have to be hired.
	 *
	 * <p>An employee has no admission number, so the identity used here is <strong>phone and name
	 * together</strong>. Neither alone would do: two teachers may share a household phone number, and
	 * two people may share a name. The pair is what the template asks for in full and is as close to a
	 * natural key as an employee gets.
	 *
	 * <p>The row number does the rest. Rows before the recorded failure were written by the earlier
	 * run, which settles the case of a row whose phone and name were edited between the two uploads —
	 * though in that case the bytes would differ and there would be no resume at all.
	 */
	private List<Hire> remaining(List<Hire> parsed, ImportJob job) {
		int failedAtRow = job.getFailedAtRow() == null ? 0 : job.getFailedAtRow();
		Set<String> onFile = identitiesOnFile(parsed);
		return parsed.stream()
				.filter(hire -> hire.row().number() >= failedAtRow)
				.filter(hire -> !onFile.contains(hire.identity()))
				.toList();
	}

	/** Which of the file's phone-and-name pairs are already employees. One query for the whole file. */
	private Set<String> identitiesOnFile(List<Hire> hires) {
		List<String> phones = hires.stream().map(hire -> hire.request().phone()).distinct().toList();
		if (phones.isEmpty()) {
			return Set.of();
		}
		return repository.findByPhoneIn(phones).stream()
				.map(employee -> identity(employee.getPhone(), employee.getName()))
				.collect(Collectors.toSet());
	}

	private static String identity(String phone, String name) {
		return phone + "|" + (name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
	}

	// --- writing ----------------------------------------------------------------------------------

	/**
	 * Hires every row: all the slow work first with no transaction open, then one short transaction per
	 * row.
	 *
	 * <p>The subjects are validated once for the file rather than once per row, which is why the rows
	 * go through the prepared-credentials form of {@link EmployeeService#hire} — the names have already
	 * been resolved to ids that exist, in {@link #resolveSubjects}.
	 */
	private ImportResult hireAll(Run run, List<Hire> hires, LocalDate today) {
		List<Planned> planned = hires.stream()
				.map(hire -> new Planned(hire, employees.prepareLogin(hire.request())))
				.toList();

		List<ImportCredentialsService.Credential> credentials = new ArrayList<>(planned.size());
		int created = 0;
		for (Planned row : planned) {
			try {
				EmployeeSavedResponse saved = employees.hire(row.hire().request(), row.login());
				created++;
				if (saved.temporaryPassword() != null) {
					credentials.add(new ImportCredentialsService.Credential(row.hire().request().name(),
							row.hire().request().employeeType().name(), saved.uniqueId(),
							saved.temporaryPassword()));
				}
			}
			catch (RuntimeException ex) {
				return stopped(run, created, credentials, row.hire().row().number(), ex, today);
			}
		}
		return finished(run, created, credentials, today);
	}

	private ImportResult finished(Run run, int created, List<ImportCredentialsService.Credential> credentials,
			LocalDate today) {
		String credentialsFileId = credentialsFiles.store(credentials);
		jobs.completed(run.key(), ImportKind.EMPLOYEES, created, run.alreadyCreated());
		// Counts only. The passwords are in the credentials file and nowhere else.
		audit.record(AuditAction.EMPLOYEES_IMPORTED, AUDIT_ENTITY, credentialsFileId, null,
				Map.of("created", created, "skipped", run.skipped(), "rows", run.rowCount(),
						"logins", credentials.size(), "importedOn", today.toString()));
		log.info("Imported {} of {} employee row(s) in {} ms (skipped {}, {} with a login)",
				created, run.rowCount(), run.elapsedMillis(), run.skipped(), credentials.size());
		return ImportResult.created(created, run.skipped(), credentialsFileId);
	}

	private ImportResult stopped(Run run, int created, List<ImportCredentialsService.Credential> credentials,
			int failedAtRow, RuntimeException failure, LocalDate today) {
		String error = describe(failure);
		String credentialsFileId = credentialsFiles.store(credentials);
		jobs.failed(run.key(), ImportKind.EMPLOYEES, created, run.alreadyCreated(), failedAtRow, error);
		audit.record(AuditAction.EMPLOYEES_IMPORTED, AUDIT_ENTITY, credentialsFileId, null,
				Map.of("created", created, "skipped", run.skipped(), "rows", run.rowCount(),
						"logins", credentials.size(), "failedAtRow", failedAtRow, "importedOn", today.toString()));
		log.error("Employee import stopped at row {} after {} of {} row(s) in {} ms (skipped {})",
				failedAtRow, created, run.rowCount(), run.elapsedMillis(), run.skipped(), failure);
		return ImportResult.partial(created, run.skipped(), credentialsFileId, failedAtRow, error);
	}

	/** One line an administrator can act on, out of an exception that may have paragraphs in it. */
	private static String describe(RuntimeException failure) {
		String message = failure.getMessage();
		if (message == null || message.isBlank()) {
			return failure.getClass().getSimpleName();
		}
		String firstLine = message.lines().findFirst().orElse(message).trim();
		return firstLine.length() <= 300 ? firstLine : firstLine.substring(0, 300) + "…";
	}

	/** One validated row: the request it became, and the row it came from so errors can name it. */
	private record Hire(ImportRow row, EmployeeRequest request) {

		/** Phone and name, lower-cased — how a re-upload recognises a row it already wrote. */
		String identity() {
			return EmployeeImportService.identity(request.phone(), request.name());
		}
	}

	/** A row with its id and password hash already computed, ready for its own short transaction. */
	private record Planned(Hire hire, PreparedLogin login) {
	}

	/**
	 * Bookkeeping for one call: what file it is, what an earlier run of it had already done, and when
	 * this one started.
	 */
	private record Run(String key, ImportJob resuming, int rowCount, int skipped, long startedNanos) {

		int alreadyCreated() {
			return resuming == null ? 0 : resuming.getCreated();
		}

		long elapsedMillis() {
			return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
		}
	}
}

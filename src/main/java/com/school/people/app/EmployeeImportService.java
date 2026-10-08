package com.school.people.app;

import java.time.Clock;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import static com.school.people.app.ImportColumns.Employees;

/**
 * Taking on a spreadsheet full of teachers and staff at once.
 *
 * <p>The counterpart to {@link StudentImportService} and built the same way: the whole file is
 * validated before a single employee is written, every error in it comes back together, and the
 * rows that pass are hired through {@link EmployeeService#hire} so an imported employee is
 * indistinguishable from a typed-in one.
 *
 * <p>What differs is the shape of a row. The {@code Type} column decides which half of it applies —
 * {@code Qualification} and {@code Subjects} for a teacher, {@code Designation} and
 * {@code Can Login} for a staff member — and the other half is <em>ignored</em> rather than
 * rejected, exactly as {@code PUT /employees/{uniqueId}} ignores it. A spreadsheet that has had both
 * kinds of column filled in for everybody is a perfectly normal thing to receive.
 *
 * <p>Not everyone imported gets a login: a staff member with {@code Can Login = NO} does not, and so
 * has no line on the credentials sheet. A file of nothing but those produces no credentials file at
 * all.
 */
@Service
public class EmployeeImportService {

	private static final String AUDIT_ENTITY = "EmployeeImport";

	private static final Logger log = LoggerFactory.getLogger(EmployeeImportService.class);

	private final EmployeeService employees;
	private final SubjectService subjects;
	private final UserService users;
	private final ImportCredentialsService credentialsFiles;
	private final AuditService audit;
	private final Clock clock;

	public EmployeeImportService(EmployeeService employees, SubjectService subjects, UserService users,
			ImportCredentialsService credentialsFiles, AuditService audit, Clock clock) {
		this.employees = employees;
		this.subjects = subjects;
		this.users = users;
		this.credentialsFiles = credentialsFiles;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Validates a workbook and, if it is clean, hires every row in it.
	 *
	 * @return either the rejection with every bad cell in the file, or the count and the id of the
	 *         credentials file — which is null when no row produced a login
	 */
	@Transactional
	public ImportResult importEmployees(MultipartFile file) {
		byte[] bytes = ImportSheet.bytesOf(file);
		LocalDate today = LocalDate.now(clock);

		List<ImportError> errors = new ArrayList<>();
		List<Hire> hires = new ArrayList<>();
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
					hires.add(hire);
				}
			}
		}

		if (errors.isEmpty()) {
			checkEmails(hires);
		}
		if (!errors.isEmpty()) {
			return ImportResult.rejected(errors);
		}

		return hireAll(hires);
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
				teacher || Boolean.TRUE.equals(canLogin)));
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
	 * <p>Checked here rather than left to the unique index, because the index would fail the insert
	 * halfway through the file — and although the transaction would undo it, the admin would get a
	 * 409 naming one address instead of a list of the rows to fix.
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

	// --- writing ----------------------------------------------------------------------------------

	private ImportResult hireAll(List<Hire> hires) {
		List<ImportCredentialsService.Credential> credentials = new ArrayList<>(hires.size());
		for (Hire hire : hires) {
			EmployeeSavedResponse saved = employees.hire(hire.request());
			if (saved.temporaryPassword() != null) {
				credentials.add(new ImportCredentialsService.Credential(hire.request().name(),
						hire.request().employeeType().name(), saved.uniqueId(), saved.temporaryPassword()));
			}
		}

		String credentialsFileId = credentialsFiles.store(credentials);
		// Counts only. The passwords are in the credentials file and nowhere else.
		audit.record(AuditAction.EMPLOYEES_IMPORTED, AUDIT_ENTITY, credentialsFileId, null,
				Map.of("created", hires.size(), "logins", credentials.size()));
		log.info("Imported {} employee(s) from a spreadsheet, {} of them with a login",
				hires.size(), credentials.size());
		return ImportResult.created(hires.size(), credentialsFileId);
	}

	/** One validated row: the request it became, and the row it came from so errors can name it. */
	private record Hire(ImportRow row, EmployeeRequest request) {
	}
}

package com.school.people.app;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.school.academics.app.SubjectService;
import com.school.auth.app.NewUser;
import com.school.auth.app.UserService;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ForbiddenException;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ValidationException;
import com.school.common.id.IdGenerator;
import com.school.common.id.IdType;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.CurrentUser;
import com.school.common.security.FieldEncryptor;
import com.school.common.security.Permission;
import com.school.common.security.Role;
import com.school.common.security.TemporaryPasswordGenerator;
import com.school.people.api.EmployeeAdminView;
import com.school.people.api.EmployeeListItem;
import com.school.people.api.EmployeeRequest;
import com.school.people.api.EmployeeSavedResponse;
import com.school.people.api.EmployeeSelfView;
import com.school.people.api.EmployeeStatusRequest;
import com.school.people.api.EmployeeView;
import com.school.people.domain.Address;
import com.school.people.domain.BankDetails;
import com.school.people.domain.Employee;
import com.school.people.domain.EmployeeStatus;
import com.school.people.domain.EmployeeType;
import com.school.people.infra.EmployeeRepository;
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
 * Employees: teachers and non-teaching staff, their logins, and their bank details.
 *
 * <p>Three things are worth knowing before changing anything here. The <strong>login is optional
 * for staff and automatic for teachers</strong>, and it can appear long after the employee does.
 * The <strong>account number is encrypted</strong> and only decrypted for the two people entitled
 * to see it. And the <strong>employee type governs which half of the document is live</strong> —
 * the other half is cleared on every write, so a teacher can never keep a designation.
 */
@Service
public class EmployeeService {

	private static final String AUDIT_ENTITY = "Employee";

	private final EmployeeRepository employees;
	private final MongoOperations mongo;
	private final SubjectService subjects;
	private final UserService users;
	private final FieldEncryptor encryptor;
	private final IdGenerator idGenerator;
	private final TemporaryPasswordGenerator temporaryPasswords;
	private final AuditService audit;
	private final Clock clock;

	public EmployeeService(EmployeeRepository employees, MongoOperations mongo, SubjectService subjects,
			UserService users, FieldEncryptor encryptor, IdGenerator idGenerator,
			TemporaryPasswordGenerator temporaryPasswords, AuditService audit, Clock clock) {
		this.employees = employees;
		this.mongo = mongo;
		this.subjects = subjects;
		this.users = users;
		this.encryptor = encryptor;
		this.idGenerator = idGenerator;
		this.temporaryPasswords = temporaryPasswords;
		this.audit = audit;
		this.clock = clock;
	}

	// --- hiring -----------------------------------------------------------------------------------

	/**
	 * Takes on an employee: issues the EMP id, writes the record, and — for a teacher, or for a staff
	 * member with {@code hasLogin} — creates the login that shares that id, all in one transaction.
	 *
	 * @return the employee, with the temporary password when a login was created and null when not
	 */
	@Transactional
	public EmployeeSavedResponse hire(EmployeeRequest request) {
		validateSubjects(request);
		String uniqueId = idGenerator.next(IdType.EMP);
		Instant now = Instant.now(clock);
		Employee employee = employees.insert(apply(Employee.builder()
				.uniqueId(uniqueId)
				.status(EmployeeStatus.ACTIVE)
				.createdAt(now)
				.updatedAt(now), request, null)
				.build());

		String temporaryPassword = createLoginIfNeeded(employee, null);
		audit.record(AuditAction.EMPLOYEE_CREATED, AUDIT_ENTITY, uniqueId, null, employee);
		return new EmployeeSavedResponse(uniqueId, temporaryPassword, adminView(employee));
	}

	// --- reading ----------------------------------------------------------------------------------

	/**
	 * One employee, for an administrator or for themselves. Anyone else gets 403: both projections
	 * carry a decrypted bank account number, so there is no safe third view to fall back to.
	 */
	public EmployeeView view(String uniqueId) {
		AuthPrincipal caller = CurrentUser.require();
		Employee employee = require(uniqueId);
		if (caller.permissions().contains(Permission.EMPLOYEE_READ)) {
			return adminView(employee);
		}
		if (employee.getUniqueId().equals(caller.uniqueId())) {
			return EmployeeSelfView.of(employee, accountNumberOf(employee));
		}
		throw new ForbiddenException("You may only read your own employee record");
	}

	/** {@code GET /employees/me}: the caller's own record. */
	public EmployeeSelfView viewSelf() {
		AuthPrincipal caller = CurrentUser.require();
		Employee employee = employees.findByUniqueId(caller.uniqueId())
				.orElseThrow(() -> new NotFoundException(
						"This login has no employee record. Only teachers and staff have one."));
		return EmployeeSelfView.of(employee, accountNumberOf(employee));
	}

	/**
	 * The filtered, paged employee list. {@code q} is an anchored prefix against the name, the
	 * uniqueId and the phone number, so every branch can use an index; the text is quoted before it
	 * reaches the regex, so a caller cannot inject one.
	 */
	public Page<Employee> search(EmployeeSearch search, Pageable pageable) {
		Query query = new Query();
		List<Criteria> criteria = new ArrayList<>();
		if (search.type() != null) {
			criteria.add(Criteria.where("employeeType").is(search.type()));
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
					Criteria.where("phone").regex(prefix)));
		}
		if (!criteria.isEmpty()) {
			query.addCriteria(new Criteria().andOperator(criteria));
		}

		long total = mongo.count(query, Employee.class);
		Pageable effective = pageable.getSort().isSorted()
				? pageable
				: PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("name"));
		return new PageImpl<>(mongo.find(query.with(effective), Employee.class), effective, total);
	}

	/** Rows carry only the stored last four digits, so no decryption happens here. */
	public List<EmployeeListItem> toListItems(List<Employee> page) {
		return page.stream().map(EmployeeListItem::of).toList();
	}

	/**
	 * Active teachers, for the picker behind {@code GET /users/teachers} and the assignment screen in
	 * academics. The employee directory is the register of who teaches — a login with the TEACHER
	 * role is only the access that goes with it.
	 */
	public List<Employee> activeTeachers() {
		return employees.findByEmployeeTypeAndStatusOrderByNameAsc(EmployeeType.TEACHER, EmployeeStatus.ACTIVE);
	}

	// --- editing ----------------------------------------------------------------------------------

	/**
	 * Replaces the editable details.
	 *
	 * <p>This is also where a staff member first gets a login: switching {@code hasLogin} on creates
	 * the account and returns its temporary password, which is why an update answers with the same
	 * shape a hire does.
	 */
	@Transactional
	public EmployeeSavedResponse update(String uniqueId, EmployeeRequest request) {
		Employee before = require(uniqueId);
		validateSubjects(request);
		String name = request.name().trim();
		Employee saved = employees.save(apply(before.toBuilder().updatedAt(Instant.now(clock)), request, before)
				.build());

		String temporaryPassword = createLoginIfNeeded(saved, before);
		// The display name in the token and in /auth/me comes from the login, not from here.
		if (!name.equals(before.getName())) {
			users.rename(saved.getUniqueId(), name);
		}
		audit.record(AuditAction.EMPLOYEE_UPDATED, AUDIT_ENTITY, saved.getUniqueId(), before, saved);
		return new EmployeeSavedResponse(saved.getUniqueId(), temporaryPassword, adminView(saved));
	}

	/**
	 * Moves an employee between ACTIVE and LEFT, and enables or disables their login to match.
	 *
	 * <p>The two have to move together. A teacher who has left but can still sign in keeps access to
	 * marks and exam papers, and — because academics checks assignability against the login — would
	 * still be assignable to a section the picker no longer offers.
	 */
	@Transactional
	public EmployeeAdminView changeStatus(String uniqueId, EmployeeStatusRequest request) {
		Employee before = require(uniqueId);
		Employee saved = employees.save(before.toBuilder()
				.status(request.status())
				.updatedAt(Instant.now(clock))
				.build());
		if (saved.isHasLogin()) {
			users.setEnabled(saved.getUniqueId(), request.status() == EmployeeStatus.ACTIVE);
		}
		audit.record(AuditAction.EMPLOYEE_STATUS_CHANGED, AUDIT_ENTITY, saved.getUniqueId(), before, saved);
		return adminView(saved);
	}

	/**
	 * Issues a fresh temporary password for an employee's login.
	 *
	 * @throws ValidationException when this employee has no login to reset — a staff member whose
	 *                             {@code hasLogin} was never switched on
	 */
	public String resetPassword(String uniqueId) {
		Employee employee = require(uniqueId);
		if (!employee.isHasLogin()) {
			throw new ValidationException("This employee has no login to reset",
					List.of(new FieldViolation("hasLogin",
							"switch it on through PUT /employees/" + employee.getUniqueId()
									+ " to create one")));
		}
		return users.resetTemporaryPassword(employee.getUniqueId());
	}

	// --- internals --------------------------------------------------------------------------------

	public Employee require(String uniqueId) {
		String normalized = UserService.normalizeUniqueId(uniqueId);
		return employees.findByUniqueId(normalized)
				.orElseThrow(() -> NotFoundException.of("Employee", normalized));
	}

	/**
	 * Everyone currently employed, by name — the register the daily employee attendance (B8) is taken
	 * against. Teachers and staff together, since both are marked on the same sheet.
	 */
	public List<EmployeeRef> activeEmployees() {
		return employees.findByStatusOrderByNameAsc(EmployeeStatus.ACTIVE).stream()
				.map(EmployeeService::toRef)
				.toList();
	}

	/** Names one employee for another module, without handing over the document. */
	public EmployeeRef requireRef(String uniqueId) {
		return toRef(require(uniqueId));
	}

	/**
	 * The lookup for callers that have a reasonable answer for "there is no such employee" — B9 prints
	 * the class teacher's name above the signature line, and a section whose teacher has since left
	 * should still produce a report card, with a blank line to sign.
	 */
	public Optional<EmployeeRef> findRef(String uniqueId) {
		if (uniqueId == null || uniqueId.isBlank()) {
			return Optional.empty();
		}
		return employees.findByUniqueId(UserService.normalizeUniqueId(uniqueId)).map(EmployeeService::toRef);
	}

	private static EmployeeRef toRef(Employee employee) {
		return new EmployeeRef(employee.getUniqueId(), employee.getName(),
				employee.getEmployeeType() == null ? null : employee.getEmployeeType().name(),
				employee.getDesignation());
	}

	/** True once the record exists, used by the local seeder to stay idempotent. */
	public boolean exists(String uniqueId) {
		return employees.existsByUniqueId(UserService.normalizeUniqueId(uniqueId));
	}

	/**
	 * Writes an employee record for a login that already exists, under that login's {@code uniqueId}.
	 *
	 * <p>Only the local seeder uses this, to back-fill the dev teacher and staff accounts that B2
	 * created before there was anywhere to describe them. It deliberately does not go through
	 * {@link #hire}: that would draw a fresh EMP number and try to create a second login.
	 *
	 * @return false if a record was already there, so a restart changes nothing
	 */
	public boolean createForExistingLogin(String uniqueId, String name, EmployeeType type, String designation) {
		String normalized = UserService.normalizeUniqueId(uniqueId);
		if (exists(normalized)) {
			return false;
		}
		Instant now = Instant.now(clock);
		employees.insert(Employee.builder()
				.uniqueId(normalized)
				.employeeType(type)
				.name(name)
				.status(EmployeeStatus.ACTIVE)
				.joiningDate(now.atZone(clock.getZone()).toLocalDate())
				.designation(type == EmployeeType.STAFF ? designation : null)
				// The login is what we are back-filling from, so by definition there is one.
				.hasLogin(true)
				.createdAt(now)
				.updatedAt(now)
				.build());
		return true;
	}

	/**
	 * Copies the request onto a builder, clearing whichever type-specific half does not apply.
	 *
	 * @param before the existing employee on an update, null on a hire. Bank details are only
	 *               re-encrypted when the request actually carries them, so an update that omits the
	 *               bank block keeps what was already on file rather than wiping it
	 */
	private Employee.EmployeeBuilder apply(Employee.EmployeeBuilder builder, EmployeeRequest request,
			Employee before) {
		boolean teacher = request.employeeType() == EmployeeType.TEACHER;
		return builder
				.employeeType(request.employeeType())
				.name(request.name().trim())
				.dob(request.dob())
				.gender(request.gender())
				.phone(trim(request.phone()))
				.email(lower(request.email()))
				.address(toAddress(request.address()))
				.joiningDate(request.joiningDate())
				.photoUrl(request.photoUrl())
				.bank(request.bank() != null ? toBank(request.bank()) : (before == null ? null : before.getBank()))
				.panLast4(trim(request.panLast4()))
				// A teacher keeps the teaching fields and loses the staff ones, and the other way round.
				.qualification(teacher ? trim(request.qualification()) : null)
				.subjectIds(teacher ? distinct(request.subjectIds()) : null)
				.experienceYears(teacher ? request.experienceYears() : null)
				.designation(teacher ? null : trim(request.designation()))
				// A teacher always has a login; for staff it is what the request asked for, and it is
				// never taken away here — removing access is what the status endpoint is for.
				.hasLogin(teacher || request.hasLogin() || (before != null && before.isHasLogin()));
	}

	/**
	 * Creates the login if this employee should have one and does not yet.
	 *
	 * @param before null on a hire; on an update, what the employee looked like before
	 * @return the temporary password, or null when nothing was created
	 */
	private String createLoginIfNeeded(Employee employee, Employee before) {
		boolean hadLogin = before != null && before.isHasLogin();
		if (!employee.isHasLogin() || hadLogin || users.exists(employee.getUniqueId())) {
			return null;
		}
		String temporaryPassword = temporaryPasswords.generate();
		Role role = employee.getEmployeeType() == EmployeeType.TEACHER ? Role.TEACHER : Role.STAFF;
		users.createWithUniqueId(employee.getUniqueId(), new NewUser(role, employee.getName(), employee.getEmail(),
				temporaryPassword, true, employee.getId()));
		return temporaryPassword;
	}

	private EmployeeAdminView adminView(Employee employee) {
		return EmployeeAdminView.of(employee, accountNumberOf(employee));
	}

	private String accountNumberOf(Employee employee) {
		return employee.getBank() == null ? null : encryptor.decrypt(employee.getBank().accountNumberEncrypted());
	}

	private BankDetails toBank(EmployeeRequest.Bank bank) {
		String accountNumber = bank.accountNumber().trim();
		return new BankDetails(
				trim(bank.accountName()),
				encryptor.encrypt(accountNumber),
				accountNumber.substring(Math.max(0, accountNumber.length() - 4)),
				upper(bank.ifsc()),
				trim(bank.bankName()));
	}

	/** Teachers may only be given subjects that exist, as elsewhere in the system. */
	private void validateSubjects(EmployeeRequest request) {
		if (request.employeeType() != EmployeeType.TEACHER || request.subjectIds() == null) {
			return;
		}
		Set<String> missing = subjects.findMissingIds(distinct(request.subjectIds()));
		if (!missing.isEmpty()) {
			throw new ValidationException("The employee refers to subjects that do not exist",
					missing.stream().map(id -> new FieldViolation("subjectIds", "no subject with id " + id)).toList());
		}
	}

	private static List<String> distinct(List<String> values) {
		return values == null ? null : List.copyOf(new LinkedHashSet<>(values));
	}

	private static Address toAddress(EmployeeRequest.Address request) {
		return request == null ? null : new Address(trim(request.line1()), trim(request.line2()),
				trim(request.city()), trim(request.state()), trim(request.postalCode()));
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String trim(String value) {
		return map(value, String::trim);
	}

	private static String lower(String value) {
		return map(value, text -> text.trim().toLowerCase(Locale.ROOT));
	}

	private static String upper(String value) {
		return map(value, text -> text.trim().toUpperCase(Locale.ROOT));
	}

	/** Blank becomes null, so an empty form field is stored as "absent" rather than as "". */
	private static String map(String value, Function<String, String> mapper) {
		return value == null || value.isBlank() ? null : mapper.apply(value);
	}
}

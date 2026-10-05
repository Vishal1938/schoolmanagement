package com.school.attendance.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.school.attendance.api.AttendanceHistoryResponse;
import com.school.attendance.api.AttendanceSummary;
import com.school.attendance.api.EmployeeRegisterResponse;
import com.school.attendance.api.MarkEmployeeAttendanceRequest;
import com.school.attendance.domain.AttendanceStatus;
import com.school.attendance.domain.EmployeeAttendance;
import com.school.attendance.domain.EmployeeAttendanceEntry;
import com.school.attendance.infra.EmployeeAttendanceRepository;
import com.school.auth.app.UserService;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.BusinessRuleException;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ForbiddenException;
import com.school.common.exceptions.ValidationException;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.CurrentUser;
import com.school.common.security.Permission;
import com.school.people.app.EmployeeRef;
import com.school.people.app.EmployeeService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Employee attendance: one register for the whole staff per day.
 *
 * <p>The same calendar rules as the student register, and the same replace-not-merge semantics. The
 * difference is who may write it — only the office, since there is no equivalent of a class teacher
 * taking their own register.
 */
@Service
public class EmployeeAttendanceService {

	private static final String AUDIT_ENTITY = "EmployeeAttendance";

	private final EmployeeAttendanceRepository attendance;
	private final EmployeeService employees;
	private final AttendanceCalendar calendar;
	private final AuditService audit;
	private final Clock clock;

	public EmployeeAttendanceService(EmployeeAttendanceRepository attendance, EmployeeService employees,
			AttendanceCalendar calendar, AuditService audit, Clock clock) {
		this.attendance = attendance;
		this.employees = employees;
		this.calendar = calendar;
		this.audit = audit;
		this.clock = clock;
	}

	// --- the daily register -----------------------------------------------------------------------

	/** Every ACTIVE employee by name, with their mark for the day or null. */
	public EmployeeRegisterResponse register(LocalDate date) {
		Optional<EmployeeAttendance> stored = attendance.findByDate(date);
		Map<String, AttendanceStatus> marks = stored.map(EmployeeAttendanceService::marksByEmployee)
				.orElseGet(Map::of);
		Markability markability = calendar.check(date, stored.map(EmployeeAttendance::getMarkedAt).orElse(null));

		List<EmployeeRegisterResponse.Row> rows = employees.activeEmployees().stream()
				.map(employee -> new EmployeeRegisterResponse.Row(employee.uniqueId(), employee.name(),
						employee.employeeType(), marks.get(employee.uniqueId())))
				.toList();

		return new EmployeeRegisterResponse(date, stored.isPresent(), markability.allowed(), markability.reason(),
				stored.map(EmployeeAttendance::getMarkedBy).orElse(null),
				stored.map(EmployeeAttendance::getMarkedAt).orElse(null),
				rows);
	}

	/** Submits or corrects the whole staff register for a day. */
	public EmployeeRegisterResponse mark(LocalDate date, MarkEmployeeAttendanceRequest request) {
		Optional<EmployeeAttendance> existing = attendance.findByDate(date);
		Markability markability = calendar.check(date, existing.map(EmployeeAttendance::getMarkedAt).orElse(null));
		if (!markability.allowed()) {
			throw new BusinessRuleException(markability.reason());
		}

		List<EmployeeAttendanceEntry> entries = validatedEntries(request);
		AuthPrincipal caller = CurrentUser.require();
		Instant now = Instant.now(clock);

		EmployeeAttendance before = existing.orElse(null);
		EmployeeAttendance toSave = existing
				.map(found -> found.toBuilder().entries(entries).markedBy(caller.uniqueId()).markedAt(now).build())
				.orElseGet(() -> EmployeeAttendance.builder()
						.date(date)
						.entries(entries)
						.markedBy(caller.uniqueId())
						.markedAt(now)
						.createdAt(now)
						.build());

		EmployeeAttendance saved = save(toSave);
		audit.record(AuditAction.EMPLOYEE_ATTENDANCE_MARKED, AUDIT_ENTITY, date.toString(), before, saved);
		return register(date);
	}

	// --- one employee's history -------------------------------------------------------------------

	/** An admin may read anyone's; an employee only their own. */
	public AttendanceHistoryResponse employeeHistory(String uniqueId, LocalDate from, LocalDate to) {
		StudentAttendanceService.requireRange(from, to);
		AuthPrincipal caller = CurrentUser.require();
		EmployeeRef employee = employees.requireRef(uniqueId);
		if (!caller.permissions().contains(Permission.EMPLOYEE_READ)
				&& !employee.uniqueId().equals(caller.uniqueId())) {
			throw new ForbiddenException("You may only read your own attendance");
		}

		List<AttendanceHistoryResponse.Day> days =
				attendance.findByEntriesEmployeeUniqueIdAndDateBetweenOrderByDateAsc(employee.uniqueId(), from, to)
						.stream()
						.flatMap(register -> register.getEntries().stream()
								.filter(entry -> employee.uniqueId().equals(entry.employeeUniqueId()))
								.map(entry -> new AttendanceHistoryResponse.Day(register.getDate(), entry.status())))
						.toList();

		return new AttendanceHistoryResponse(employee.uniqueId(), employee.name(), from, to, days,
				AttendanceSummary.of(days.stream().map(AttendanceHistoryResponse.Day::status).toList()));
	}

	/**
	 * What one employee's register costs them over a range, for payroll proration (B13).
	 *
	 * <p><strong>No authorization check, and none is missing.</strong> This is not reachable from a
	 * request: a payroll run is already behind {@code PAYROLL_MANAGE}, and what comes back is two
	 * counts rather than anybody's day-by-day record. The narrow shape is the point — payroll has no
	 * business reading the register itself.
	 *
	 * @return {@link UnpaidDays#none()} for an empty or inverted range, so a caller never has to
	 *         bounds-check a month it worked out itself
	 */
	public UnpaidDays unpaidDays(String employeeUniqueId, LocalDate from, LocalDate to) {
		if (employeeUniqueId == null || from == null || to == null || to.isBefore(from)) {
			return UnpaidDays.none();
		}
		String normalized = UserService.normalizeUniqueId(employeeUniqueId);
		int absent = 0;
		int half = 0;
		for (EmployeeAttendance register : attendance
				.findByEntriesEmployeeUniqueIdAndDateBetweenOrderByDateAsc(normalized, from, to)) {
			AttendanceStatus status = marksByEmployee(register).get(normalized);
			if (status == AttendanceStatus.ABSENT) {
				absent++;
			}
			else if (status == AttendanceStatus.HALF_DAY) {
				half++;
			}
		}
		return new UnpaidDays(absent, half);
	}

	// --- internals --------------------------------------------------------------------------------

	private List<EmployeeAttendanceEntry> validatedEntries(MarkEmployeeAttendanceRequest request) {
		Set<String> active = employees.activeEmployees().stream()
				.map(EmployeeRef::uniqueId)
				.collect(Collectors.toSet());
		List<FieldViolation> violations = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		List<EmployeeAttendanceEntry> entries = new ArrayList<>();

		List<MarkEmployeeAttendanceRequest.Entry> requested = request.entries();
		for (int i = 0; i < requested.size(); i++) {
			MarkEmployeeAttendanceRequest.Entry entry = requested.get(i);
			String employeeUniqueId = UserService.normalizeUniqueId(entry.employeeUniqueId());
			String field = "entries[" + i + "].employeeUniqueId";
			if (!active.contains(employeeUniqueId)) {
				violations.add(new FieldViolation(field, employeeUniqueId + " is not an active employee"));
				continue;
			}
			if (!seen.add(employeeUniqueId)) {
				violations.add(new FieldViolation(field, employeeUniqueId + " is listed twice"));
				continue;
			}
			entries.add(new EmployeeAttendanceEntry(employeeUniqueId, entry.status()));
		}

		if (!violations.isEmpty()) {
			throw new ValidationException("The register could not be saved", violations);
		}
		return entries;
	}

	private EmployeeAttendance save(EmployeeAttendance register) {
		try {
			return attendance.save(register);
		}
		catch (DuplicateKeyException ex) {
			throw new ConflictException(
					"This register was submitted by somebody else a moment ago. Reload and try again.", ex);
		}
	}

	private static Map<String, AttendanceStatus> marksByEmployee(EmployeeAttendance register) {
		return register.getEntries() == null ? Map.of() : register.getEntries().stream()
				.collect(Collectors.toMap(EmployeeAttendanceEntry::employeeUniqueId, EmployeeAttendanceEntry::status,
						(first, second) -> first));
	}
}

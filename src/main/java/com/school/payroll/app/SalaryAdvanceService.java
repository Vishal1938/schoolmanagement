package com.school.payroll.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ValidationException;
import com.school.payroll.api.AdvanceRequest;
import com.school.payroll.domain.AdvanceStatus;
import com.school.payroll.domain.SalaryAdvance;
import com.school.payroll.infra.SalaryAdvanceRepository;
import com.school.people.app.EmployeeRef;
import com.school.people.app.EmployeeService;
import org.springframework.stereotype.Service;

/**
 * Advances against salary, and their recovery.
 *
 * <p>An advance is written once and then only ever recovered — there is no edit and no delete. The
 * balance moves in exactly one place, {@link #recover(SalaryAdvance, long)}, which is called when a
 * payroll record is <em>paid</em>. A PENDING record is only a plan: deleting one and re-running the
 * month must not have taken anything off what an employee owes.
 */
@Service
public class SalaryAdvanceService {

	static final String AUDIT_ENTITY = "SalaryAdvance";

	private final SalaryAdvanceRepository advances;
	private final EmployeeService employees;
	private final AuditService audit;
	private final Clock clock;

	public SalaryAdvanceService(SalaryAdvanceRepository advances, EmployeeService employees, AuditService audit,
			Clock clock) {
		this.advances = advances;
		this.employees = employees;
		this.audit = audit;
		this.clock = clock;
	}

	// --- writing ----------------------------------------------------------------------------------

	/** Records money handed over, to be recovered from the next runs onwards. */
	public SalaryAdvance give(AdvanceRequest request) {
		EmployeeRef employee = employees.requireRef(request.employeeUniqueId());
		LocalDate today = LocalDate.now(clock);
		LocalDate givenOn = request.givenOn() == null ? today : request.givenOn();
		validate(request, givenOn, today);

		Instant now = Instant.now(clock);
		SalaryAdvance saved = advances.insert(SalaryAdvance.builder()
				.employeeUniqueId(employee.uniqueId())
				.employeeName(employee.name())
				.amount(request.amount())
				.givenOn(givenOn)
				.monthlyRecovery(request.monthlyRecovery())
				.recoveredSoFar(0L)
				.status(AdvanceStatus.ACTIVE)
				.reason(request.reason() == null || request.reason().isBlank() ? null : request.reason().trim())
				.createdAt(now)
				.updatedAt(now)
				.build());

		audit.record(AuditAction.SALARY_ADVANCE_CREATED, AUDIT_ENTITY, saved.getId(), null, saved);
		return saved;
	}

	// --- reading ----------------------------------------------------------------------------------

	/**
	 * Advances for one employee, newest first; or every advance still being recovered when no employee
	 * is named. Cleared advances are only ever shown per employee, because the list the office wants
	 * open is "who still owes the school money".
	 */
	public List<SalaryAdvance> list(String employeeUniqueId) {
		if (employeeUniqueId == null || employeeUniqueId.isBlank()) {
			return advances.findByStatusOrderByGivenOnDesc(AdvanceStatus.ACTIVE);
		}
		return advances.findByEmployeeUniqueIdOrderByGivenOnDesc(
				employees.requireRef(employeeUniqueId).uniqueId());
	}

	/** What a run should deduct from, oldest advance first so the earliest one clears first. */
	List<SalaryAdvance> activeFor(String employeeUniqueId) {
		return advances.findByEmployeeUniqueIdAndStatusOrderByGivenOnAsc(employeeUniqueId, AdvanceStatus.ACTIVE);
	}

	Optional<SalaryAdvance> find(String id) {
		return advances.findById(id);
	}

	// --- recovery ---------------------------------------------------------------------------------

	/**
	 * Takes an installment off an advance, closing it when nothing is left.
	 *
	 * <p>Called from inside the transaction that pays a salary, and never from a controller. The
	 * amount is capped at what is actually left: a run plans an installment, and by the time the
	 * salary goes out the balance may be smaller than the plan — a record for an earlier month paid in
	 * between, say. Paying out more than the plan is never possible, and recovering more than is owed
	 * is never possible either.
	 *
	 * @return what was actually recovered, in paise. Zero when the advance was already clear
	 */
	long recover(SalaryAdvance advance, long planned) {
		long applied = Math.min(Math.max(0L, planned), advance.remaining());
		if (applied == 0L) {
			return 0L;
		}
		long recovered = advance.getRecoveredSoFar() + applied;
		SalaryAdvance saved = advances.save(advance.toBuilder()
				.recoveredSoFar(recovered)
				.status(recovered >= advance.getAmount() ? AdvanceStatus.CLOSED : AdvanceStatus.ACTIVE)
				.updatedAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.SALARY_ADVANCE_RECOVERED, AUDIT_ENTITY, saved.getId(), advance, saved);
		return applied;
	}

	// --- validation -------------------------------------------------------------------------------

	private void validate(AdvanceRequest request, LocalDate givenOn, LocalDate today) {
		List<FieldViolation> violations = new ArrayList<>();
		if (givenOn.isAfter(today)) {
			violations.add(new FieldViolation("givenOn", "is in the future; an advance is recorded once given"));
		}
		if (request.monthlyRecovery() > request.amount()) {
			violations.add(new FieldViolation("monthlyRecovery",
					"is more than the advance itself (" + request.amount() + " paise)"));
		}
		if (!violations.isEmpty()) {
			throw new ValidationException("The advance could not be recorded", violations);
		}
	}
}

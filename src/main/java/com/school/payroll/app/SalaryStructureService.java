package com.school.payroll.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ValidationException;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.CurrentUser;
import com.school.payroll.api.SalaryStructureRequest;
import com.school.payroll.api.SalaryStructureResponse;
import com.school.payroll.domain.DeductionRule;
import com.school.payroll.domain.DeductionType;
import com.school.payroll.domain.SalaryComponent;
import com.school.payroll.domain.SalaryStructure;
import com.school.payroll.infra.SalaryStructureRepository;
import com.school.people.app.EmployeeRef;
import com.school.people.app.EmployeeService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Salary structures: what each employee is paid, and the history of every change to it.
 *
 * <p>The one rule that governs this class is that <strong>nothing is ever overwritten</strong>. A PUT
 * inserts the next version and leaves its predecessors alone, because a payroll record and the slip
 * printed from it have to stay explainable years later, and because "what was this person on in
 * April" is a question schools get asked.
 *
 * <p>Which version applies is therefore always a question about a date. Reading the structure asks it
 * about today; a payroll run asks it about the month it is paying for, through
 * {@link #effectiveFor(String, LocalDate)}.
 */
@Service
public class SalaryStructureService {

	private static final String AUDIT_ENTITY = "SalaryStructure";

	/** 100% in basis points: a deduction may take all of the basic, but not more than it exists. */
	private static final long FULL_PERCENT = 10_000L;

	private final SalaryStructureRepository structures;
	private final EmployeeService employees;
	private final AuditService audit;
	private final Clock clock;

	public SalaryStructureService(SalaryStructureRepository structures, EmployeeService employees,
			AuditService audit, Clock clock) {
		this.structures = structures;
		this.employees = employees;
		this.audit = audit;
		this.clock = clock;
	}

	// --- reading ----------------------------------------------------------------------------------

	/**
	 * What this employee is on today, plus every version there has ever been.
	 *
	 * <p>An employee with no structure yet is not a 404: the employee exists, and "nothing agreed yet"
	 * is exactly what the office needs to see on the screen where they agree it. {@code current} and
	 * {@code history} come back empty instead.
	 */
	public SalaryStructureResponse view(String employeeUniqueId) {
		EmployeeRef employee = employees.requireRef(employeeUniqueId);
		List<SalaryStructure> history = structures.findByEmployeeUniqueIdOrderByVersionDesc(employee.uniqueId());
		return new SalaryStructureResponse(
				employee.uniqueId(),
				employee.name(),
				effectiveFor(employee.uniqueId(), LocalDate.now(clock))
						.map(SalaryStructureResponse.Version::of)
						.orElse(null),
				history.stream().map(SalaryStructureResponse.Version::of).toList());
	}

	/**
	 * The version in force on a date: the latest {@code effectiveFrom} not after it.
	 *
	 * <p>Package-private, and the only way a payroll run gets at a structure. A future-dated version —
	 * a raise agreed in advance — is invisible until its date arrives.
	 */
	Optional<SalaryStructure> effectiveFor(String employeeUniqueId, LocalDate on) {
		return structures
				.findFirstByEmployeeUniqueIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(
						employeeUniqueId, on);
	}

	// --- writing ----------------------------------------------------------------------------------

	/**
	 * Saves the next version of an employee's structure.
	 *
	 * @return the same shape a GET returns, so the caller sees what is now current — which is
	 *         <em>not</em> necessarily what was just saved, if it was dated into the future
	 * @throws ConflictException 409 when two admins save a version at the same moment; the unique
	 *                           index on (employee, version) decides it and the loser reloads
	 */
	public SalaryStructureResponse save(String employeeUniqueId, SalaryStructureRequest request) {
		EmployeeRef employee = employees.requireRef(employeeUniqueId);
		validate(request);

		SalaryStructure previous = structures.findFirstByEmployeeUniqueIdOrderByVersionDesc(employee.uniqueId())
				.orElse(null);
		SalaryStructure structure = SalaryStructure.builder()
				.employeeUniqueId(employee.uniqueId())
				.version(previous == null ? 1 : previous.getVersion() + 1)
				.basic(request.basic())
				.allowances(allowances(request))
				.deductions(deductions(request))
				.effectiveFrom(request.effectiveFrom())
				.createdBy(currentUniqueId())
				.createdAt(Instant.now(clock))
				.build();

		SalaryStructure saved;
		try {
			saved = structures.insert(structure);
		}
		catch (DuplicateKeyException ex) {
			throw new ConflictException("Somebody else saved a new version of this structure a moment ago. "
					+ "Reload and try again.", ex);
		}

		audit.record(AuditAction.SALARY_STRUCTURE_VERSIONED, AUDIT_ENTITY, employee.uniqueId(), previous, saved);
		return view(employee.uniqueId());
	}

	// --- validation bean validation cannot express ------------------------------------------------

	/**
	 * Three things the annotations cannot say: a name may not appear twice in either list, a
	 * percentage may not exceed 100%, and the deductions may not come to more than the whole salary.
	 *
	 * <p>The last one matters because an over-deducting structure does not fail — it quietly pays
	 * everybody zero, every month, until somebody notices.
	 */
	private void validate(SalaryStructureRequest request) {
		List<FieldViolation> violations = new ArrayList<>();

		Set<String> allowanceNames = new HashSet<>();
		List<SalaryStructureRequest.Allowance> allowances = request.allowances() == null
				? List.of()
				: request.allowances();
		for (int i = 0; i < allowances.size(); i++) {
			String name = allowances.get(i).name().trim();
			if (!allowanceNames.add(name.toLowerCase(Locale.ROOT))) {
				violations.add(new FieldViolation("allowances[" + i + "].name", name + " is listed twice"));
			}
		}

		Set<String> deductionNames = new HashSet<>();
		List<SalaryStructureRequest.Deduction> deductions = request.deductions() == null
				? List.of()
				: request.deductions();
		long deductionTotal = 0L;
		for (int i = 0; i < deductions.size(); i++) {
			SalaryStructureRequest.Deduction deduction = deductions.get(i);
			String name = deduction.name().trim();
			if (!deductionNames.add(name.toLowerCase(Locale.ROOT))) {
				violations.add(new FieldViolation("deductions[" + i + "].name", name + " is listed twice"));
			}
			if (deduction.type() == DeductionType.PERCENT_OF_BASIC && deduction.value() > FULL_PERCENT) {
				violations.add(new FieldViolation("deductions[" + i + "].value",
						"is " + deduction.value() + " basis points, which is more than 100% of the basic"));
			}
			deductionTotal += deduction.type().amountOf(request.basic(), deduction.value());
		}

		long gross = request.basic() + allowances.stream()
				.mapToLong(SalaryStructureRequest.Allowance::amount)
				.sum();
		if (deductionTotal > gross) {
			violations.add(new FieldViolation("deductions",
					"come to " + deductionTotal + " paise, which is more than the gross of " + gross));
		}

		if (!violations.isEmpty()) {
			throw new ValidationException("The salary structure is not usable", violations);
		}
	}

	// --- internals --------------------------------------------------------------------------------

	private static List<SalaryComponent> allowances(SalaryStructureRequest request) {
		return request.allowances() == null ? List.of() : request.allowances().stream()
				.map(allowance -> new SalaryComponent(allowance.name().trim(), allowance.amount()))
				.toList();
	}

	private static List<DeductionRule> deductions(SalaryStructureRequest request) {
		return request.deductions() == null ? List.of() : request.deductions().stream()
				.map(deduction -> new DeductionRule(deduction.name().trim(), deduction.type(), deduction.value()))
				.toList();
	}

	/** Who saved this version, for the record itself; the audit trail has the fuller actor. */
	private static String currentUniqueId() {
		return CurrentUser.principal().map(AuthPrincipal::uniqueId).orElse(null);
	}
}

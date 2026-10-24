package com.school.payroll.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.school.attendance.app.EmployeeAttendanceService;
import com.school.attendance.app.UnpaidDays;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.config.AppProperties;
import com.school.common.exceptions.BusinessRuleException;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ForbiddenException;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ValidationException;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.CurrentUser;
import com.school.common.security.Permission;
import com.school.payroll.api.MyPayrollResponse;
import com.school.payroll.api.PayBulkRequest;
import com.school.payroll.api.PayBulkResponse;
import com.school.payroll.api.PayRecordRequest;
import com.school.payroll.api.PayrollMonthResponse;
import com.school.payroll.api.PayrollRecordResponse;
import com.school.payroll.api.PayrollRunRequest;
import com.school.payroll.api.PayrollRunResponse;
import com.school.payroll.domain.AdvanceRecovery;
import com.school.payroll.domain.AppliedDeduction;
import com.school.payroll.domain.DeductionRule;
import com.school.payroll.domain.PayrollRecord;
import com.school.payroll.domain.PayrollStatus;
import com.school.payroll.domain.SalaryAdvance;
import com.school.payroll.domain.SalaryComponent;
import com.school.payroll.domain.SalaryPaymentMode;
import com.school.payroll.domain.SalaryStructure;
import com.school.payroll.infra.PayrollRecordRepository;
import com.school.people.app.EmployeeRef;
import com.school.people.app.EmployeeService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payroll runs, the records they produce, and paying them out.
 *
 * <p>Three things decide the shape of this class.
 *
 * <p><strong>A record is a snapshot.</strong> Everything that went into the figure — the employee's
 * name and designation, the structure version, every allowance, every deduction as costed — is
 * copied in at run time and never recomputed. A raise in November does not change October's slip.
 *
 * <p><strong>A run is idempotent and not transactional.</strong> The unique index on
 * {@code (month, employeeUniqueId)} is what makes running the same month twice safe, so each
 * employee is written on their own and an employee already done is simply skipped. One transaction
 * around the whole run would mean that one duplicate aborted everybody — and a half-finished run is
 * fine here, because finishing it is what running it again does.
 *
 * <p><strong>Advance balances move when money moves.</strong> A PENDING record only <em>plans</em> a
 * recovery; paying it is what takes the installment off the advance. A run therefore has to look at
 * what other pending records have already planned, or two unpaid months would both deduct the same
 * last installment.
 */
@Service
public class PayrollService {

	static final String AUDIT_ENTITY = "PayrollRecord";

	/** Entity type for the run itself, whose id is the month. */
	private static final String RUN_AUDIT_ENTITY = "PayrollRun";

	private final PayrollRecordRepository records;
	private final SalaryStructureService structures;
	private final SalaryAdvanceService advances;
	private final EmployeeService employees;
	private final EmployeeAttendanceService attendance;
	private final AuditService audit;
	private final AppProperties properties;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	public PayrollService(PayrollRecordRepository records, SalaryStructureService structures,
			SalaryAdvanceService advances, EmployeeService employees, EmployeeAttendanceService attendance,
			AuditService audit, AppProperties properties, ApplicationEventPublisher events, Clock clock) {
		this.records = records;
		this.structures = structures;
		this.advances = advances;
		this.employees = employees;
		this.attendance = attendance;
		this.audit = audit;
		this.properties = properties;
		this.events = events;
		this.clock = clock;
	}

	// --- the run ----------------------------------------------------------------------------------

	/**
	 * Computes one month's salaries: one PENDING record per active employee who has a structure
	 * effective for the month.
	 *
	 * <p>Safe to run as often as you like. Employees who already have a record for the month are
	 * skipped, so the normal way to finish a run is to fix whatever was wrong — usually a missing
	 * structure — and run it again.
	 *
	 * @throws BusinessRuleException 422 for a month that has not happened yet
	 */
	public PayrollRunResponse run(PayrollRunRequest request) {
		PayrollRunResult result = runFor(Months.parse(request.month(), "month"), request.prorateByAttendance());
		return new PayrollRunResponse(result.month(), result.prorateByAttendance(), result.created(),
				result.skipped(), result.missingStructure());
	}

	/**
	 * The same run, taking a {@link YearMonth} and answering in this module's own type.
	 *
	 * <p>For a caller in another module that runs several months in a row — B20's demonstration data
	 * seeder, which runs July to October and pays the first three.
	 */
	public PayrollRunResult runFor(YearMonth month, boolean prorateByAttendance) {
		if (month.isAfter(YearMonth.from(LocalDate.now(clock)))) {
			throw new BusinessRuleException("Payroll cannot be run for " + month
					+ ", which has not happened yet. There is no attendance to prorate by and no month to pay for.");
		}

		String key = Months.format(month);
		// A structure dated into the month applies to it: a raise agreed from the 15th is what that
		// person was on when the month ended, and splitting a month across two structures is not
		// something a school payslip does.
		LocalDate asOf = month.atEndOfMonth();
		Set<String> already = records.findByMonthOrderByEmployeeNameAsc(key).stream()
				.map(PayrollRecord::getEmployeeUniqueId)
				.collect(Collectors.toSet());

		String runBy = currentUniqueId();
		int created = 0;
		int skipped = 0;
		List<String> missingStructure = new ArrayList<>();

		for (EmployeeRef employee : employees.activeEmployees()) {
			if (already.contains(employee.uniqueId())) {
				skipped++;
				continue;
			}
			Optional<SalaryStructure> structure = structures.effectiveFor(employee.uniqueId(), asOf);
			if (structure.isEmpty()) {
				missingStructure.add(employee.name() + " (" + employee.uniqueId() + ")");
				continue;
			}
			PayrollRecord computed = compute(employee, structure.get(), month, prorateByAttendance, runBy);
			try {
				PayrollRecord saved = records.insert(computed);
				audit.record(AuditAction.PAYROLL_RECORD_CREATED, AUDIT_ENTITY, saved.getId(), null, saved);
				created++;
			}
			catch (DuplicateKeyException ex) {
				// Another run got there between the read above and this insert. The index is the real
				// guarantee; the pre-read is only there to keep the common case off this path.
				skipped++;
			}
		}

		PayrollRunResult result = new PayrollRunResult(key, prorateByAttendance, created, skipped,
				List.copyOf(missingStructure));
		// Counts rather than every record: each record has its own PAYROLL_RECORD_CREATED entry.
		audit.record(AuditAction.PAYROLL_RUN, RUN_AUDIT_ENTITY, key, null, Map.of(
				"month", key,
				"prorateByAttendance", prorateByAttendance,
				"created", created,
				"skipped", skipped,
				"missingStructure", missingStructure));
		return result;
	}

	/**
	 * One employee's figure for one month, start to finish.
	 *
	 * <pre>
	 * gross   = basic + allowances
	 * perDay  = gross / days in the month
	 * lossOfPay = perDay * ABSENT days + half a perDay per HALF_DAY     (prorated runs only)
	 * net     = gross - deductions - lossOfPay - advance recovery
	 * </pre>
	 *
	 * <p>Deductions are costed against the <em>full</em> basic, not a prorated one: PF and
	 * professional tax are what they are regardless of how many days somebody turned up.
	 */
	private PayrollRecord compute(EmployeeRef employee, SalaryStructure structure, YearMonth month, boolean prorate,
			String runBy) {
		long basic = structure.getBasic();
		long gross = structure.gross();

		List<AppliedDeduction> deductions = (structure.getDeductions() == null
				? List.<DeductionRule>of()
				: structure.getDeductions()).stream()
				.map(rule -> new AppliedDeduction(rule.name(), rule.type(), rule.value(), rule.amountOf(basic)))
				.toList();
		long deductionTotal = deductions.stream().mapToLong(AppliedDeduction::amount).sum();

		int daysInMonth = month.lengthOfMonth();
		UnpaidDays unpaid = prorate
				? attendance.unpaidDays(employee.uniqueId(), month.atDay(1), month.atEndOfMonth())
				: UnpaidDays.none();
		long lossOfPay = 0L;
		if (!unpaid.isNil()) {
			long perDay = gross / daysInMonth;
			// Capped at the gross: somebody absent every single day earns nothing, never less.
			lossOfPay = Math.min(gross, perDay * unpaid.absentDays() + perDay * unpaid.halfDays() / 2);
		}

		// What is left to take an advance installment out of. Recovery is capped at this, so a record
		// never plans to recover money that was not actually withheld from anybody.
		long payable = Math.max(0L, gross - deductionTotal - lossOfPay);
		List<AdvanceRecovery> recoveries = plannedRecoveries(employee.uniqueId(), payable);
		long advanceRecovery = recoveries.stream().mapToLong(AdvanceRecovery::amount).sum();

		Instant now = Instant.now(clock);
		return PayrollRecord.builder()
				.month(Months.format(month))
				.employeeUniqueId(employee.uniqueId())
				.employeeName(employee.name())
				.employeeType(employee.employeeType())
				.designation(employee.designation())
				.structureId(structure.getId())
				.structureVersion(structure.getVersion())
				.basic(basic)
				.allowances(structure.getAllowances() == null ? List.<SalaryComponent>of() : structure.getAllowances())
				.gross(gross)
				.deductions(deductions)
				.deductionTotal(deductionTotal)
				.advanceRecoveries(recoveries)
				.advanceRecovery(advanceRecovery)
				.prorated(prorate)
				.daysInMonth(daysInMonth)
				.absentDays(unpaid.absentDays())
				.halfDays(unpaid.halfDays())
				.lossOfPay(lossOfPay)
				.net(payable - advanceRecovery)
				.status(PayrollStatus.PENDING)
				.runBy(runBy)
				.createdAt(now)
				.updatedAt(now)
				.build();
	}

	/**
	 * This month's installment against each advance the employee is still paying off, oldest first.
	 *
	 * <p>What is left of an advance is its balance <em>less whatever other PENDING records have
	 * already planned</em> against it. Without that, running September and October before paying
	 * either would deduct the final installment twice and the second payment would silently recover
	 * nothing.
	 *
	 * @param room the most that can come out of this month's salary, in paise
	 */
	private List<AdvanceRecovery> plannedRecoveries(String employeeUniqueId, long room) {
		if (room <= 0L) {
			return List.of();
		}
		List<SalaryAdvance> active = advances.activeFor(employeeUniqueId);
		if (active.isEmpty()) {
			return List.of();
		}

		Map<String, Long> alreadyPlanned = new HashMap<>();
		for (PayrollRecord pending : records.findByEmployeeUniqueIdAndStatus(employeeUniqueId,
				PayrollStatus.PENDING)) {
			for (AdvanceRecovery planned : pending.getAdvanceRecoveries() == null
					? List.<AdvanceRecovery>of()
					: pending.getAdvanceRecoveries()) {
				alreadyPlanned.merge(planned.advanceId(), planned.amount(), Long::sum);
			}
		}

		List<AdvanceRecovery> recoveries = new ArrayList<>();
		long left = room;
		for (SalaryAdvance advance : active) {
			if (left <= 0L) {
				break;
			}
			long outstanding = advance.remaining() - alreadyPlanned.getOrDefault(advance.getId(), 0L);
			// The last installment is whatever is left of the advance, not the full monthly figure.
			long amount = Math.min(Math.min(advance.getMonthlyRecovery(), outstanding), left);
			if (amount <= 0L) {
				continue;
			}
			recoveries.add(new AdvanceRecovery(advance.getId(), amount));
			left -= amount;
		}
		return recoveries;
	}

	// --- reading ----------------------------------------------------------------------------------

	/**
	 * One month's register and its totals.
	 *
	 * <p>Empty rather than 404 for a month nobody has run: "not computed yet" is the normal state of
	 * next month, and the screen is the same one you run it from.
	 */
	public PayrollMonthResponse month(String month) {
		String key = Months.format(Months.parse(month, "month"));
		List<PayrollRecord> found = records.findByMonthOrderByEmployeeNameAsc(key);
		long gross = found.stream().mapToLong(PayrollRecord::getGross).sum();
		long net = found.stream().mapToLong(PayrollRecord::getNet).sum();
		long paid = found.stream().filter(record -> !record.isPending()).mapToLong(PayrollRecord::getNet).sum();
		return new PayrollMonthResponse(key, toResponses(found),
				new PayrollMonthResponse.Totals(gross, net, paid, net - paid));
	}

	/**
	 * What the current month's payroll still owes, for the admin dashboard (B16).
	 *
	 * <p>The current month rather than a month the caller picks: the dashboard asks "what do I owe
	 * right now", and the register screen is where another month is looked up.
	 *
	 * <p>No caller check — it returns two figures and no employee — so the endpoint that calls it is
	 * behind {@code DASHBOARD_ADMIN}.
	 */
	public PendingSalaries pendingThisMonth() {
		String month = Months.format(YearMonth.now(clock));
		List<PayrollRecord> pending = records.findByMonthOrderByEmployeeNameAsc(month).stream()
				.filter(PayrollRecord::isPending)
				.toList();
		return new PendingSalaries(month, pending.size(), pending.stream().mapToLong(PayrollRecord::getNet).sum());
	}

	/** The caller's own salary records, newest month first, with what they have and have not been paid. */
	public MyPayrollResponse mine() {
		AuthPrincipal caller = CurrentUser.require();
		List<PayrollRecord> found = records.findByEmployeeUniqueIdOrderByMonthDesc(caller.uniqueId());
		long paid = found.stream().filter(record -> !record.isPending()).mapToLong(PayrollRecord::getNet).sum();
		long unpaid = found.stream().filter(PayrollRecord::isPending).mapToLong(PayrollRecord::getNet).sum();
		return new MyPayrollResponse(toResponses(found), new MyPayrollResponse.Totals(paid, unpaid));
	}

	/**
	 * One record, for an administrator, an accountant, or the employee it belongs to.
	 *
	 * <p>The object-level half of the slip's authorization, as CLAUDE.md rule 1 requires: the
	 * annotation on the endpoint only decides who may ask at all.
	 *
	 * <p>{@code PAYROLL_READ} is here beside {@code PAYROLL_MANAGE} because salaries are the largest
	 * line in the expense book and the books have to be able to show them (B21). Reading a slip is all
	 * it allows — running a month, paying one out and touching a structure stay with the administrator.
	 */
	public PayrollRecord requireReadable(String id) {
		PayrollRecord record = require(id);
		AuthPrincipal caller = CurrentUser.require();
		if (caller.permissions().contains(Permission.PAYROLL_MANAGE)
				|| caller.permissions().contains(Permission.PAYROLL_READ)
				|| record.getEmployeeUniqueId().equals(caller.uniqueId())) {
			return record;
		}
		throw new ForbiddenException("You may only read your own salary records");
	}

	/** Where the slip for a record is served from. */
	public String slipUrl(PayrollRecord record) {
		return properties.apiBasePath() + "/payroll/records/" + record.getId() + "/slip.pdf";
	}

	// --- paying out -------------------------------------------------------------------------------

	/**
	 * Marks one salary paid, recovering this month's advance installments as it goes.
	 *
	 * @throws ConflictException 409 if it has already been paid. Paying twice would recover the
	 *                           advance installment twice
	 */
	@Transactional
	public PayrollRecordResponse pay(String id, PayRecordRequest request) {
		PayrollRecord paid = payRecord(id, request.mode(), request.reference(), request.paidOn());
		return PayrollRecordResponse.of(paid, slipUrl(paid));
	}

	/**
	 * The same, in this module's own types rather than the controller's request body.
	 *
	 * <p>For a caller in another module paying a whole month off record by record, each with its own
	 * reference — B20's demonstration data seeder, whose references carry the month and the employee
	 * id. {@code @Transactional} is repeated because the advance recovery and the record have to stand
	 * or fall together, and a self-call from {@link #pay} would miss the proxy.
	 *
	 * @param paidOn the day the money left; null means today, and a date in the future is refused
	 */
	@Transactional
	public PayrollRecord payRecord(String id, SalaryPaymentMode mode, String reference, LocalDate paidOn) {
		return markPaid(require(id), mode, reference, requirePayableDate(paidOn));
	}

	/**
	 * The PENDING records of one month, for a caller about to pay them.
	 *
	 * <p>No authorization check, and none is missing: it is not reachable from a request, and the
	 * caller — B20's demonstration data seeder — is behind {@code DEMO_DATA_MANAGE}. Anything a person
	 * reads goes through {@link #month(String)} or {@link #requireReadable(String)}.
	 */
	/**
	 * Every salary that has actually been paid, in the order it went out — what the accounts backfill
	 * (B21) walks to book the months that predate the module.
	 *
	 * <p>No authorization check, for the same reason as {@link #pendingRecords(YearMonth)}: it is not
	 * reachable from a request, and the endpoint behind it is an administrator's.
	 */
	public List<PayrollRecord> paidRecords() {
		return records.findByStatusOrderByPaidOnAsc(PayrollStatus.PAID);
	}

	public List<PayrollRecord> pendingRecords(YearMonth month) {
		return records.findByMonthOrderByEmployeeNameAsc(Months.format(month)).stream()
				.filter(PayrollRecord::isPending)
				.toList();
	}

	/**
	 * Whether any salary has been computed for any month in this range, both ends inclusive.
	 *
	 * <p>The guard that stops the demonstration seeder running on top of a real payroll: a record is
	 * what a slip is printed from, and a second run would not replace one — it would skip it and leave
	 * the two data sets interleaved.
	 */
	public boolean anyRecordIn(YearMonth from, YearMonth to) {
		return !records.findByMonthBetweenOrderByMonthAsc(Months.format(from), Months.format(to)).isEmpty();
	}

	/**
	 * One bank run, many salaries.
	 *
	 * <p>Forgiving on purpose. A record already paid, or an id that no longer exists because the month
	 * was re-run, is reported back rather than failing the batch — one stale id should not stop fifty
	 * people being paid. Replaying the same list therefore pays nothing twice.
	 */
	@Transactional
	public PayBulkResponse payBulk(PayBulkRequest request) {
		LocalDate paidOn = requirePayableDate(request.paidOn());
		List<String> alreadyPaid = new ArrayList<>();
		List<String> notFound = new ArrayList<>();
		int paid = 0;
		long netPaid = 0L;

		for (String id : new LinkedHashSet<>(request.recordIds())) {
			Optional<PayrollRecord> found = records.findById(id);
			if (found.isEmpty()) {
				notFound.add(id);
				continue;
			}
			if (!found.get().isPending()) {
				alreadyPaid.add(id);
				continue;
			}
			// No reference: that is what distinguishes a bulk transfer from paying one record.
			PayrollRecord record = markPaid(found.get(), request.mode(), null, paidOn);
			paid++;
			netPaid += record.getNet();
		}
		return new PayBulkResponse(paid, alreadyPaid, notFound, netPaid);
	}

	/**
	 * Throws away a PENDING record so its month can be re-run — the way to fix a figure that came out
	 * of a wrong structure.
	 *
	 * @throws ConflictException 409 for a record already paid. A paid salary is a fact; correcting one
	 *                           is a decision for the office, not a DELETE
	 */
	public void delete(String id) {
		PayrollRecord record = require(id);
		if (!record.isPending()) {
			throw new ConflictException("This salary was paid on " + record.getPaidOn()
					+ " and cannot be deleted. Only a PENDING record can.");
		}
		records.delete(record);
		audit.record(AuditAction.PAYROLL_RECORD_DELETED, AUDIT_ENTITY, id, record, null);
	}

	// --- internals --------------------------------------------------------------------------------

	/**
	 * The one path that moves a record to PAID, shared by the single and bulk endpoints so the advance
	 * recovery and the audit entry cannot differ between them.
	 */
	private PayrollRecord markPaid(PayrollRecord record, SalaryPaymentMode mode, String reference,
			LocalDate paidOn) {
		if (!record.isPending()) {
			throw new ConflictException("This salary was already paid on " + record.getPaidOn()
					+ " by " + record.getMode() + ".");
		}

		// What each advance actually gave back, which the books need and which is not always what the
		// run planned — see SalaryAdvanceRecoveredEvent. Collected rather than published as we go,
		// because nothing is announced until the record itself has been written.
		Map<String, Long> recovered = new LinkedHashMap<>();
		for (AdvanceRecovery recovery : record.getAdvanceRecoveries() == null
				? List.<AdvanceRecovery>of()
				: record.getAdvanceRecoveries()) {
			// The advance may have moved since the run planned this; recover() caps at what is owed and
			// closes the advance when the last of it comes back.
			advances.find(recovery.advanceId())
					.ifPresent(advance -> recovered.put(advance.getId(), advances.recover(advance, recovery.amount())));
		}

		PayrollRecord saved = records.save(record.toBuilder()
				.status(PayrollStatus.PAID)
				.mode(mode)
				.reference(reference == null || reference.isBlank() ? null : reference.trim())
				.paidOn(paidOn)
				.updatedAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.PAYROLL_RECORD_PAID, AUDIT_ENTITY, saved.getId(), record, saved);

		events.publishEvent(new PayrollRecordPaidEvent(saved.getId(), saved.getEmployeeUniqueId(),
				saved.getEmployeeName(), saved.getMonth(), saved.getNet(), saved.getMode(), saved.getReference(),
				saved.getPaidOn()));
		recovered.forEach((advanceId, amount) -> {
			if (amount > 0L) {
				events.publishEvent(new SalaryAdvanceRecoveredEvent(advanceId, saved.getId(),
						saved.getEmployeeUniqueId(), saved.getEmployeeName(), amount, saved.getPaidOn()));
			}
		});
		return saved;
	}

	/** Salaries are recorded as they go out, so the date may be backdated but never postdated. */
	private LocalDate requirePayableDate(LocalDate paidOn) {
		LocalDate today = LocalDate.now(clock);
		if (paidOn == null) {
			return today;
		}
		if (paidOn.isAfter(today)) {
			throw new ValidationException("The payment date is in the future",
					List.of(new FieldViolation("paidOn", "a salary is recorded when it goes out, not before")));
		}
		return paidOn;
	}

	private PayrollRecord require(String id) {
		return records.findById(id).orElseThrow(() -> NotFoundException.of("Payroll record", id));
	}

	private List<PayrollRecordResponse> toResponses(List<PayrollRecord> found) {
		return found.stream().map(record -> PayrollRecordResponse.of(record, slipUrl(record))).toList();
	}

	private static String currentUniqueId() {
		return CurrentUser.principal().map(AuthPrincipal::uniqueId).orElse(null);
	}
}

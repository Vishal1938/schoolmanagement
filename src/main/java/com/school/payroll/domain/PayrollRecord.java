package com.school.payroll.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One employee's salary for one month: everything that went into the figure, frozen.
 *
 * <p><strong>A snapshot, not a view.</strong> The employee's name and designation, the structure's
 * version, every allowance and every deduction are copied in at run time, so the record — and the
 * slip printed from it — says what the school actually decided that month even after a raise, a
 * transfer or a correction to the structure. Nothing here is recomputed on read.
 *
 * <p>Money is {@code long} paise throughout, per CLAUDE.md rule 3.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(PayrollRecord.COLLECTION)
// This is what makes a payroll run idempotent: a second run for the same month hits this index on
// every employee it has already done, rather than paying the staff twice.
@CompoundIndex(name = "payroll_records_month_employee_idx",
		def = "{'month': 1, 'employeeUniqueId': 1}", unique = true)
// /payroll/mine, newest month first.
@CompoundIndex(name = "payroll_records_employee_month_idx",
		def = "{'employeeUniqueId': 1, 'month': -1}")
public class PayrollRecord {

	public static final String COLLECTION = "payroll_records";

	@Id
	private String id;

	/** The month this is salary for, as {@code yyyy-MM}. Sorts and ranges as a plain string. */
	private String month;

	// --- who, as of the run -----------------------------------------------------------------------

	private String employeeUniqueId;

	private String employeeName;

	/** TEACHER or STAFF, as a string: the people module's enum stays in the people module. */
	private String employeeType;

	/** The staff job title, null for a teacher. The slip falls back to the type. */
	private String designation;

	// --- what they were on ------------------------------------------------------------------------

	private String structureId;

	private int structureVersion;

	private long basic;

	private List<SalaryComponent> allowances;

	/** Basic plus allowances, in paise. */
	private long gross;

	// --- what came off ----------------------------------------------------------------------------

	private List<AppliedDeduction> deductions;

	/** Sum of {@link #deductions}, in paise. */
	private long deductionTotal;

	/** Which advances this month's installment goes against, and for how much. */
	private List<AdvanceRecovery> advanceRecoveries;

	/** Sum of {@link #advanceRecoveries}, in paise. Taken off the balances when the record is paid. */
	private long advanceRecovery;

	/** Whether the run was asked to prorate by attendance. False means lossOfPay is 0 by decision. */
	private boolean prorated;

	/** Calendar days in the month — the divisor behind the per-day rate. */
	private int daysInMonth;

	private int absentDays;

	private int halfDays;

	/** Pay docked for absence, in paise. Zero unless the run was prorated. */
	private long lossOfPay;

	/** Gross less deductions, advance recovery and loss of pay, floored at zero. In paise. */
	private long net;

	// --- paying out -------------------------------------------------------------------------------

	private PayrollStatus status;

	private LocalDate paidOn;

	private SalaryPaymentMode mode;

	/** UTR, cheque number or UPI reference. Null for cash, and for a bulk transfer. */
	private String reference;

	/** The admin whose run created this record, by uniqueId. */
	private String runBy;

	private Instant createdAt;

	private Instant updatedAt;

	/** True while the record may still be re-run, deleted or paid. */
	public boolean isPending() {
		return status == PayrollStatus.PENDING;
	}
}

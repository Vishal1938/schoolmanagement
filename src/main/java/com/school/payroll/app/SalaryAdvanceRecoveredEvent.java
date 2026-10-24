package com.school.payroll.app;

import java.time.LocalDate;

/**
 * An installment of an advance came back, because a salary was paid.
 *
 * <p>The counterpart of {@link SalaryAdvanceGivenEvent}: it reduces what the school is owed rather
 * than earning anything, so the accounts module (B21) books it on the income side of the same
 * "Staff advances" asset category. Neither side shows up in the month's income or expense.
 *
 * <p>{@code amount} is what was <em>actually</em> recovered, which can be less than the installment
 * the run planned — the balance may have moved since, and recovery is capped at what is still owed.
 * Zero recoveries are not announced at all.
 *
 * @param payrollRecordId the salary that recovered it. Together with {@code advanceId} this is the
 *                        idempotency key: one record recovers from one advance once
 */
public record SalaryAdvanceRecoveredEvent(
		String advanceId,
		String payrollRecordId,
		String employeeUniqueId,
		String employeeName,
		long amount,
		LocalDate recoveredOn) {
}

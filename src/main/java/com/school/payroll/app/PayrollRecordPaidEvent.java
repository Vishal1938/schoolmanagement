package com.school.payroll.app;

import java.time.LocalDate;

import com.school.payroll.domain.SalaryPaymentMode;

/**
 * A salary has actually gone out.
 *
 * <p>Published once per record from the single path that moves one to {@code PAID}, so paying one
 * salary and paying fifty in a bank run announce the same thing fifty times rather than differently
 * once. The accounts module (B21) books each as an expense.
 *
 * <p>{@code net} and not {@code gross} is what leaves the bank: deductions and the advance
 * installment never move, so booking the gross would overstate the month's expense by every PF
 * contribution the school withheld.
 *
 * @param recordId the payroll record, and the idempotency key the accounts entry is written under: a
 *                 record can be paid once only, so the entry is written once only
 * @param month    {@code yyyy-MM}, the month this is salary <em>for</em> — not when it was paid
 * @param net      what went out, in paise
 */
public record PayrollRecordPaidEvent(
		String recordId,
		String employeeUniqueId,
		String employeeName,
		String month,
		long net,
		SalaryPaymentMode mode,
		String reference,
		LocalDate paidOn) {
}

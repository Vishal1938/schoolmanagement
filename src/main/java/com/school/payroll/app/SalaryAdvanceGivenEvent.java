package com.school.payroll.app;

import java.time.LocalDate;

/**
 * Money was handed to an employee ahead of their salary.
 *
 * <p>Booked by the accounts module (B21) against the "Staff advances" system category, which is an
 * <strong>asset and not an expense</strong>: the cash has left the drawer, so a money account's
 * balance must move, but the school is owed it back and no month's expense total should include it.
 *
 * @param advanceId the advance, and the idempotency key of the entry written from it
 * @param amount    the whole advance, in paise
 */
public record SalaryAdvanceGivenEvent(
		String advanceId,
		String employeeUniqueId,
		String employeeName,
		long amount,
		LocalDate givenOn,
		String reason) {
}

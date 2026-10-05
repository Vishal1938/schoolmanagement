package com.school.fees.api;

import java.time.LocalDate;

/**
 * One row of {@code GET /fees/reports/defaulters}. All amounts are paise.
 *
 * @param balance       what is still owed across every unsettled invoice, including the late fine
 *                      running right now
 * @param lateFineDue   the part of {@code balance} that is fine rather than fee
 * @param oldestDueDate the due date of the oldest invoice still owed — what the office sorts on
 * @param invoiceCount  how many invoices are unsettled
 */
public record DefaulterResponse(
		String studentUniqueId,
		String name,
		String classId,
		String className,
		String section,
		long balance,
		long lateFineDue,
		LocalDate oldestDueDate,
		int invoiceCount) {
}

package com.school.payroll.api;

import java.util.List;

/**
 * {@code GET /payroll/runs/{month}}: the month's register and what it comes to.
 *
 * @param records by employee name. Empty rather than 404 for a month nobody has run — "nothing has
 *                been computed yet" is a normal answer and the UI shows the same screen either way
 */
public record PayrollMonthResponse(String month, List<PayrollRecordResponse> records, Totals totals) {

	/**
	 * All in paise.
	 *
	 * @param gross   sum of the gross, before anything was withheld
	 * @param net     sum of the net, whether paid or not — the month's wage bill
	 * @param paid    net of the PAID records: money actually out
	 * @param pending net of the PENDING records, so {@code paid + pending == net}
	 */
	public record Totals(long gross, long net, long paid, long pending) {
	}
}

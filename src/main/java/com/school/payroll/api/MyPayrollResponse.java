package com.school.payroll.api;

import java.util.List;

/**
 * {@code GET /payroll/mine}: an employee's own salary records, newest month first.
 *
 * @param records empty rather than 404 for a login with no payroll yet — a teacher who joined this
 *                month has nothing to show, which is not an error
 */
public record MyPayrollResponse(List<PayrollRecordResponse> records, Totals totals) {

	/**
	 * In paise.
	 *
	 * @param paid   net of the records already paid
	 * @param unpaid net of the records still PENDING
	 */
	public record Totals(long paid, long unpaid) {
	}
}

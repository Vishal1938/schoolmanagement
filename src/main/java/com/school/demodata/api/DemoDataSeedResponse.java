package com.school.demodata.api;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.school.demodata.app.DemoSummary;

/**
 * What one seeding run produced. Every amount is paise.
 *
 * @param paymentsByMode    how many payments came in each way and what they came to, keyed by
 *                          {@code CASH}, {@code CHEQUE}, {@code UPI_OFFLINE} or
 *                          {@code BANK_TRANSFER}. The demonstration data takes nothing online
 * @param totalCollected    principal and late fines together, which is what the collection report for
 *                          the whole session will show
 * @param receiptsIssued    one per payment. Their numbers ascend with their dates, because the seeder
 *                          takes every payment in date order
 * @param payrollByMonth    keyed by {@code yyyy-MM}. The last month is run and left entirely pending,
 *                          so the admin dashboard has salaries to owe
 * @param invoicesSkipped   invoices that already existed. Generation is idempotent, so this is only
 *                          ever non-zero on a re-run
 * @param studentsMarkedLeft and {@code employeesMarkedLeft}: the one withdrawn student and the one
 *                          departed member of staff, both after their money had been dealt with
 * @param warnings          everything the data set named and could not find, or that was refused. Not
 *                          failures: a run with warnings still produced everything else
 */
public record DemoDataSeedResponse(
		int feeHeads,
		int feeStructures,
		int holidays,
		int invoicesCreated,
		int invoicesSkipped,
		int individualCharges,
		int payments,
		Map<String, ModeTotal> paymentsByMode,
		long totalCollected,
		int receiptsIssued,
		int salaryStructures,
		int salaryAdvances,
		int attendanceDaysMarked,
		int attendanceMarks,
		Map<String, PayrollMonth> payrollByMonth,
		int studentsMarkedLeft,
		int employeesMarkedLeft,
		List<String> warnings) {

	public static DemoDataSeedResponse of(DemoSummary summary) {
		// Sorted maps, so the JSON reads in mode order and in month order rather than in whatever
		// order the seeder happened to take the money in.
		Map<String, ModeTotal> byMode = new TreeMap<>();
		summary.paymentsByMode().forEach((mode, total) ->
				byMode.put(mode.name(), new ModeTotal(total.count(), total.amount())));
		Map<String, PayrollMonth> byMonth = new TreeMap<>();
		summary.payrollByMonth().forEach((month, counts) ->
				byMonth.put(month, new PayrollMonth(counts.created(), counts.paid(), counts.pending())));

		return new DemoDataSeedResponse(
				summary.feeHeads(),
				summary.feeStructures(),
				summary.holidays(),
				summary.invoicesCreated(),
				summary.invoicesSkipped(),
				summary.individualCharges(),
				summary.payments(),
				byMode,
				summary.totalCollected(),
				summary.receiptsIssued(),
				summary.salaryStructures(),
				summary.salaryAdvances(),
				summary.attendanceDaysMarked(),
				summary.attendanceMarks(),
				byMonth,
				summary.studentsMarkedLeft(),
				summary.employeesMarkedLeft(),
				summary.warnings());
	}

	/** @param amount in paise */
	public record ModeTotal(int count, long amount) {
	}

	/** @param pending {@code created - paid}; the last month seeded is entirely pending */
	public record PayrollMonth(int created, int paid, int pending) {
	}
}

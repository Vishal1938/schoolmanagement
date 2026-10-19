package com.school.fees.app;

import java.time.YearMonth;
import java.util.List;

/**
 * The fee figures the admin dashboard shows, in paise, as other modules see them.
 *
 * <p>Built in one pass over a session's invoices — see {@link FeeDashboardService} — so the whole
 * thing costs the same few reads whether the institution has a hundred students or two thousand.
 *
 * <p><strong>Late fines are in none of these figures.</strong> A fine is a function of today's date
 * rather than something stored, so including it would make "pending" a number that moves on its own
 * overnight and reconciles against nothing. The defaulters list is where a fine is quoted, because
 * that is the screen somebody acts on. {@code pending} here is unpaid principal, nothing else.
 *
 * <p>Cancelled invoices are excluded throughout: a withdrawn charge is history, not money owed.
 */
public record FeeDashboard(
		SessionTotals session,
		MonthTotals month,
		List<ClassMonth> monthByClass,
		List<PendingOneTime> pendingOneTime) {

	/** What a deployment with no active session, or no invoices yet, reports. */
	public static FeeDashboard empty(YearMonth month) {
		return new FeeDashboard(new SessionTotals(0L, 0L, 0L), new MonthTotals(month, 0L, 0L, 0L),
				List.of(), List.of());
	}

	/**
	 * The whole active session, every kind of charge together.
	 *
	 * @param totalDue  sum of the net amounts billed
	 * @param collected principal taken against them
	 * @param pending   what is left, so {@code collected + pending == totalDue} unless a concession
	 *                  was applied after a payment
	 */
	public record SessionTotals(long totalDue, long collected, long pending) {
	}

	/** The same three figures for the current month's MONTHLY installments only — the footer of {@link #monthByClass}. */
	public record MonthTotals(YearMonth month, long totalDue, long collected, long pending) {
	}

	/**
	 * One class-section's standing on the current month's MONTHLY installment.
	 *
	 * <p><strong>{@code paidCount + unpaidCount} can be less than {@code students}.</strong> A student
	 * on the roll who has not been billed this month — admitted after invoices were generated, or in a
	 * class with no fee structure — is in neither count, because they have neither paid nor failed to.
	 * Reporting them as paid would overstate collection and as unpaid would invent a debt.
	 *
	 * @param students    ACTIVE students on this class-section's roll
	 * @param paidCount   of those, the ones whose month is settled in full
	 * @param unpaidCount of those, the ones still carrying a balance on it
	 */
	public record ClassMonth(
			String classId,
			String className,
			String section,
			int students,
			int paidCount,
			int unpaidCount,
			long collected,
			long pending) {
	}

	/**
	 * A one-off charge of this session that somebody still owes — an admission fee, a lab deposit —
	 * one row per charge per class.
	 *
	 * <p>Counted straight off the invoices rather than against the roll, so a student who has since
	 * left is still here: money owed does not stop being owed because somebody left.
	 *
	 * @param label       the charge as it was named when it was raised
	 * @param unpaidCount students with a balance on it
	 * @param pending     what they owe between them
	 */
	public record PendingOneTime(String label, String classId, String className, int unpaidCount, long pending) {
	}
}

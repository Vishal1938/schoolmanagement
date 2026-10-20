package com.school.fees.app;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.school.fees.domain.FeePeriod;

/**
 * The fee figures the admin dashboard shows, in paise, as other modules see them.
 *
 * <p>Built in one pass over a session's invoices — see {@link FeeDashboardService} — so the whole
 * thing costs the same few reads whether the institution has a hundred students or two thousand.
 *
 * <p><strong>The period is the institution's own, not the calendar's.</strong> A school collects month
 * by month and a college semester by semester, so "where do we stand right now" means a different
 * stretch of the year in each. {@link #currentPeriod} is whichever one covers today, and
 * {@link #currentPeriodByClass} counts each student against the recurring installment covering today —
 * their month or their semester, whichever their course actually charges.
 *
 * <p>{@link #month} and {@link #monthByClass} are the same two figures under their old names, kept for
 * one release so a frontend built against the monthly-only shape keeps working. On a monthly
 * deployment they are identical to the period pair; on a semester one they carry the semester's
 * figures, because a stale client showing the right money under a heading of "this month" is better
 * than one showing zeros.
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
		CurrentPeriod currentPeriod,
		List<ClassPeriod> currentPeriodByClass,
		MonthTotals month,
		List<ClassPeriod> monthByClass,
		List<PendingOneTime> pendingOneTime) {

	/**
	 * What a deployment with no active session, or no invoices yet, reports.
	 *
	 * @param window the period to head the empty screen with, which with nothing billed can only come
	 *               from the configured fee cycle
	 */
	public static FeeDashboard empty(LocalDate today, FeePeriod window) {
		return new FeeDashboard(
				new SessionTotals(0L, 0L, 0L),
				new CurrentPeriod(window.describe(), window.start(), window.end(), 0L, 0L, 0L),
				List.of(),
				new MonthTotals(YearMonth.from(today), 0L, 0L, 0L),
				List.of(),
				List.of());
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

	/**
	 * The stretch of the year being collected right now, and where it stands institution-wide — the
	 * footer of {@link #currentPeriodByClass}.
	 *
	 * <p>There is deliberately no installment name here. On a monthly deployment every class calls this
	 * period the same thing, but on a semester one two courses covering the same months may call them
	 * "Semester 1" and "Semester 3", so a single key would be wrong for somebody. The per-class rows
	 * carry the label each class actually uses; this is the heading above them.
	 *
	 * @param label how to head the period — {@code "Oct 2026"}, {@code "Semester 3 (Jul-Dec 2026)"}, or
	 *              {@code "Jul-Dec 2026"} when the courses underneath it do not agree on a name
	 * @param start first day of the period, {@code end} the last, inclusive
	 */
	public record CurrentPeriod(
			String label,
			LocalDate start,
			LocalDate end,
			long totalDue,
			long collected,
			long pending) {
	}

	/**
	 * The old monthly-only form of {@link CurrentPeriod}, kept for one release.
	 *
	 * @param month the calendar month containing today, whatever period the figures are actually for
	 */
	public record MonthTotals(YearMonth month, long totalDue, long collected, long pending) {
	}

	/**
	 * One class-section's standing on the recurring installment covering today.
	 *
	 * <p><strong>{@code paidCount + unpaidCount} can be less than {@code students}.</strong> A student
	 * on the roll who has not been billed for this period — admitted after invoices were generated, or in
	 * a class with no fee structure — is in neither count, because they have neither paid nor failed to.
	 * Reporting them as paid would overstate collection and as unpaid would invent a debt.
	 *
	 * @param label       what this class calls the period its students are being counted against —
	 *                    {@code "Oct 2026"} or {@code "Semester 3 (Jul-Dec 2026)"}. Null for a section
	 *                    nobody has been billed for, where there is no installment to name
	 * @param students    ACTIVE students on this class-section's roll
	 * @param paidCount   of those, the ones whose period is settled in full
	 * @param unpaidCount of those, the ones still carrying a balance on it
	 */
	public record ClassPeriod(
			String classId,
			String className,
			String section,
			String label,
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

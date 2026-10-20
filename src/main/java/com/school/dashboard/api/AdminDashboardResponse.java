package com.school.dashboard.api;

import java.time.LocalDate;
import java.util.List;

import com.school.attendance.app.DayAttendance;
import com.school.exams.app.ExamsOverview;
import com.school.fees.app.FeeDashboard;
import com.school.payment.app.DayCollection;
import com.school.payment.app.PaymentSummary;
import com.school.payroll.app.PendingSalaries;
import com.school.quiz.app.QuizzesOverview;

/**
 * {@code GET /dashboard/admin}: one screen's worth of the institution, in one request.
 *
 * <p><strong>Every money figure is paise</strong> (CLAUDE.md rule 3), and every one of them is a sum
 * of stored amounts rather than anything re-derived — see {@link FeeDashboard} for what is and is not
 * counted.
 *
 * <p><strong>A section for a module that is switched off is absent, not empty.</strong> Jackson is
 * configured {@code non_null}, so {@code exams}, {@code notices}, {@code quizzes} and
 * {@code studentAttendance} simply do not appear in the body when their module is off. That is the
 * same promise the rest of the API makes — an institution that does not run quizzes looks like a
 * build that never had them — and it is distinguishable from a module that is on but has nothing in
 * it yet, which comes back as a section full of zeros.
 *
 * <p>{@code session} is null on a deployment with no active academic session. The rest still
 * renders, with empty fee sections, so the administrator can reach the screen that fixes it rather
 * than meeting a 422 on login.
 *
 * @param asOf                      the school's today, in the deployment's own zone
 * @param studentAttendance         today's register tally. Absent when the student attendance module
 *                                  is off
 * @param todayCollection           money taken today, every mode together
 * @param feesSession               the active session's billing, every kind of charge together
 * @param feesCurrentPeriod         the stretch of the year being collected right now — a month or a
 *                                  semester, per {@code academicSettings.feeCycle} and what the
 *                                  invoices actually say — institution-wide: the footer of
 *                                  {@code feesCurrentPeriodByClass}
 * @param feesCurrentPeriodByClass  one row per class-section, in class order then section
 * @param feesThisMonth             <strong>deprecated</strong>, the same figures as
 *                                  {@code feesCurrentPeriod} under the old name. Kept for one release
 * @param feesThisMonthByClass      <strong>deprecated</strong>, the same rows as
 *                                  {@code feesCurrentPeriodByClass}. Kept for one release
 * @param pendingOneTimeFees        outstanding one-off charges, heaviest first
 * @param pendingSalaries           this month's computed-but-unpaid payroll
 * @param recentPayments            the last ten receipts, newest first
 * @param exams                     absent when the exams module is off
 * @param notices                   absent when the notices module is off
 * @param quizzes                   absent when the quizzes module is off
 */
public record AdminDashboardResponse(
		LocalDate asOf,
		Session session,
		Counts counts,
		DayAttendance studentAttendance,
		DayCollection todayCollection,
		FeeDashboard.SessionTotals feesSession,
		FeeDashboard.CurrentPeriod feesCurrentPeriod,
		List<FeeDashboard.ClassPeriod> feesCurrentPeriodByClass,
		FeeDashboard.MonthTotals feesThisMonth,
		List<FeeDashboard.ClassPeriod> feesThisMonthByClass,
		List<FeeDashboard.PendingOneTime> pendingOneTimeFees,
		PendingSalaries pendingSalaries,
		List<PaymentSummary> recentPayments,
		ExamsOverview exams,
		Notices notices,
		QuizzesOverview quizzes) {

	/** The academic session every figure below is scoped to. */
	public record Session(String id, String name) {
	}

	/**
	 * Head counts.
	 *
	 * @param students ACTIVE students enrolled in the active session; {@code 0} when none is active
	 * @param teachers ACTIVE employees of type TEACHER
	 * @param staff    ACTIVE employees who are not teachers
	 * @param classes  classes configured, and the class-sections they add up to
	 */
	public record Counts(int students, int teachers, int staff, int classes, int sections) {
	}

	/** Wrapped rather than a bare number so that "the module is off" is one absent field. */
	public record Notices(long active) {
	}
}

package com.school.demodata.app;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Every date the demonstration data set is built around, in one place.
 *
 * <p>Fixed rather than derived from today, because the whole point of the data is to be <em>already
 * in the past</em>: a fee that fell due in July and was paid in August, a fine that had run for ten
 * days when the cheque arrived, three months of salaries paid and one still pending. Dates relative
 * to "now" would give a different story every time it was run and no late fine at all on the day it
 * was written.
 *
 * <p>It therefore describes the 2026-27 session specifically, which is what
 * {@code DemoDataSeeder} insists is active before it writes anything.
 */
final class DemoCalendar {

	// --- the session ------------------------------------------------------------------------------

	static final LocalDate SESSION_START = LocalDate.of(2026, 7, 1);

	static final LocalDate SESSION_END = LocalDate.of(2027, 6, 30);

	// --- the two semesters ------------------------------------------------------------------------

	/** First semester: July to December, falling due at the end of the opening month. */
	static final LocalDate SEMESTER_A_START = LocalDate.of(2026, 7, 1);

	static final LocalDate SEMESTER_A_END = LocalDate.of(2026, 12, 31);

	static final LocalDate SEMESTER_A_DUE = LocalDate.of(2026, 7, 31);

	/** Second semester: January to June. */
	static final LocalDate SEMESTER_B_START = LocalDate.of(2027, 1, 1);

	static final LocalDate SEMESTER_B_END = LocalDate.of(2027, 6, 30);

	static final LocalDate SEMESTER_B_DUE = LocalDate.of(2027, 1, 31);

	// --- the one-off fees -------------------------------------------------------------------------

	static final LocalDate ADMISSION_FEE_DUE = LocalDate.of(2026, 7, 20);

	static final LocalDate EXAM_FEE_A_DUE = LocalDate.of(2026, 11, 15);

	static final LocalDate EXAM_FEE_B_DUE = LocalDate.of(2027, 4, 15);

	// --- when the five patterns pay ---------------------------------------------------------------

	/** P0, "on time": before both the admission fee and the first semester fall due. */
	static final LocalDate PAID_ON_TIME = LocalDate.of(2026, 7, 18);

	/** P0's exam fee, five weeks before it falls due. */
	static final LocalDate PAID_EXAM_FEE = LocalDate.of(2026, 10, 10);

	/** P1, "late with fine": ten days past the first semester's grace period. */
	static final LocalDate PAID_LATE = LocalDate.of(2026, 8, 25);

	/** P2, "partial": the first instalment, still inside the grace period. */
	static final LocalDate PAID_PART_ONE = LocalDate.of(2026, 8, 5);

	/** P2's second instalment, by which time a fine has been running for four weeks. */
	static final LocalDate PAID_PART_TWO = LocalDate.of(2026, 9, 12);

	/** P4, "paid in advance": both semesters at once, with the first semester's fine capped. */
	static final LocalDate PAID_IN_ADVANCE = LocalDate.of(2026, 10, 1);

	// --- the individual charges -------------------------------------------------------------------

	static final LocalDate LIBRARY_FINE_DUE = LocalDate.of(2026, 10, 31);

	static final LocalDate CERTIFICATE_FEE_DUE = LocalDate.of(2026, 9, 10);

	static final LocalDate CERTIFICATE_FEE_PAID = LocalDate.of(2026, 9, 12);

	static final LocalDate BTC_LIBRARY_FINE_DUE = LocalDate.of(2026, 9, 30);

	// --- staff ------------------------------------------------------------------------------------

	/** When every salary structure takes effect: the first day of the session. */
	static final LocalDate SALARIES_FROM = LocalDate.of(2026, 7, 1);

	/** The one revision, so a payroll run before October and one after differ by one person's basic. */
	static final LocalDate REVISION_FROM = LocalDate.of(2026, 10, 1);

	static final LocalDate ADVANCE_GIVEN_ON = LocalDate.of(2026, 8, 1);

	/** The day the one withdrawn student and the one departing employee leave. */
	static final LocalDate LEFT_ON = LocalDate.of(2026, 9, 30);

	// --- payroll ----------------------------------------------------------------------------------

	/** The three months that are run and paid. */
	static final List<YearMonth> PAID_PAYROLL_MONTHS = List.of(
			YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9));

	/** The month that is run and deliberately left PENDING, so the dashboard has salaries to owe. */
	static final YearMonth PENDING_PAYROLL_MONTH = YearMonth.of(2026, 10);

	// --- the two public holidays ------------------------------------------------------------------

	static final LocalDate INDEPENDENCE_DAY = LocalDate.of(2026, 8, 15);

	static final LocalDate GANDHI_JAYANTI = LocalDate.of(2026, 10, 2);

	private DemoCalendar() {
	}
}

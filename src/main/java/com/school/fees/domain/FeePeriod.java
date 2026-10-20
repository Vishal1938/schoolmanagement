package com.school.fees.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The stretch of the session one recurring installment covers, both ends inclusive.
 *
 * <p><strong>Why this is stored for a semester and derived for a month.</strong> A monthly
 * installment's period is implied by its due date — "Jul 2026" falling due on the 10th covers July —
 * so every installment written before this existed can be read correctly without a migration, which
 * is what {@link #ofMonthContaining} is for. A semester's cannot: "Semester 3" due on 31 July covers
 * July to December, and nothing about the due date says so. So a SEMESTER installment carries its
 * period explicitly and a MONTHLY one may leave it out.
 *
 * @param start first day covered
 * @param end   last day covered, inclusive
 */
public record FeePeriod(LocalDate start, LocalDate end) {

	/** {@code "Jul 2026"} — how a single-month period reads. English, to match the installment names. */
	private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMM uuuu", Locale.ENGLISH);

	/** {@code "Jul"} — the short month on its own, for the two ends of a longer period. */
	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);

	/** The whole calendar month that date falls in, which is what a MONTHLY installment covers. */
	public static FeePeriod ofMonthContaining(LocalDate date) {
		YearMonth month = YearMonth.from(date);
		return new FeePeriod(month.atDay(1), month.atEndOfMonth());
	}

	/** Both ends inclusive, so a due date on the last day of a semester is still inside it. */
	public boolean covers(LocalDate date) {
		return date != null && !date.isBefore(start) && !date.isAfter(end);
	}

	/** True when this period and that one share at least one day. */
	public boolean overlaps(FeePeriod other) {
		return !start.isAfter(other.end) && !other.start.isAfter(end);
	}

	/**
	 * How to word this period: {@code "Jul 2026"} for a single month, {@code "Jul-Dec 2026"} within one
	 * year, {@code "Oct 2026-Mar 2027"} across two.
	 *
	 * <p>Used to build a semester's label and to head the dashboard's current period when the
	 * installments underneath it do not agree on a name.
	 */
	public String describe() {
		YearMonth from = YearMonth.from(start);
		YearMonth to = YearMonth.from(end);
		if (from.equals(to)) {
			return MONTH_YEAR.format(start);
		}
		if (from.getYear() == to.getYear()) {
			return MONTH.format(start) + "-" + MONTH_YEAR.format(end);
		}
		return MONTH_YEAR.format(start) + "-" + MONTH_YEAR.format(end);
	}
}

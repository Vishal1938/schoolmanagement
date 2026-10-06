package com.school.fees.app;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.school.fees.domain.Invoice;
import com.school.fees.domain.LateFineRule;
import com.school.fees.domain.LateFineType;

/**
 * What an invoice's late fine comes to today.
 *
 * <p>Pure and static on purpose. The fine is <strong>never stored</strong> — a figure that depends on
 * today's date would be wrong by tomorrow — so this runs on every read of an invoice, and being a
 * function of its arguments is what makes that safe.
 */
public final class LateFine {

	/**
	 * The fine still owed on this invoice, in paise.
	 *
	 * <p>Nothing is charged while anything of the principal is still within the grace period, nothing
	 * is charged once the invoice is settled or cancelled, and whatever has already been collected as
	 * a fine is taken off.
	 *
	 * @param rule  the structure's rule, or null when the school charges nothing for being late
	 * @param today the date to charge as of, which comes from the injected {@code Clock}
	 */
	public static long dueOn(Invoice invoice, LateFineRule rule, LocalDate today) {
		// A settled or withdrawn invoice stops accruing: otherwise an invoice paid on time in April
		// would show a growing fine for the rest of the year.
		if (invoice.outstanding() <= 0L) {
			return 0L;
		}
		long accrued = accruedOn(invoice.getDueDate(), rule, today);
		return Math.max(0L, accrued - invoice.getLateFinePaid());
	}

	/** The gross fine for a due date under a rule, before anything already collected is taken off. */
	public static long accruedOn(LocalDate dueDate, LateFineRule rule, LocalDate today) {
		if (rule == null || dueDate == null || rule.amount() <= 0L) {
			return 0L;
		}
		LocalDate chargeableFrom = dueDate.plusDays(Math.max(0, rule.graceDays()));
		long daysLate = ChronoUnit.DAYS.between(chargeableFrom, today);
		if (daysLate <= 0L) {
			// The grace period ends *at the end of* that day, so the fine starts the day after.
			return 0L;
		}
		long fine = rule.type() == LateFineType.PER_DAY ? rule.amount() * daysLate : rule.amount();
		// A cap of 0 means uncapped. It only really matters for PER_DAY, where an invoice nobody chased
		// would otherwise grow a fine larger than the fee itself.
		return rule.cap() > 0L ? Math.min(fine, rule.cap()) : fine;
	}

	/** Past the due date plus the grace days with something still owed. */
	public static boolean isOverdue(Invoice invoice, LateFineRule rule, LocalDate today) {
		if (invoice.outstanding() <= 0L || invoice.getDueDate() == null) {
			return false;
		}
		int graceDays = rule == null ? 0 : Math.max(0, rule.graceDays());
		return today.isAfter(invoice.getDueDate().plusDays(graceDays));
	}

	private LateFine() {
	}
}

package com.school.fees.app;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import com.school.fees.domain.InstallmentKind;
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
	 * This invoice's late-fine rule out of a map keyed by structure id — <strong>the only safe way to
	 * read one</strong>.
	 *
	 * <p>A {@link InstallmentKind#CUSTOM} charge has <em>no</em> {@code structureId}: it is raised
	 * against one student and belongs to no structure, so there is no rule to read and it carries no
	 * late fine. Looking that null up directly is what used to throw: the map comes from
	 * {@link FeeStructureService#lateFineRulesOf}, which returned {@code Map.of()} when none of the
	 * invoices had a structure, and {@code Map.of().get(null)} is a NullPointerException rather than
	 * null. A ledger holding nothing but individual charges — a library fine on its own — therefore
	 * crashed every quote, every allocation and every status read. Call this instead of
	 * {@code rules.get(invoice.getStructureId())}.
	 *
	 * @return the rule, or null when this invoice has no structure or its structure charges nothing
	 * @throws IllegalStateException if an invoice that <em>should</em> have a structure has none. That
	 *                               is a corrupt document rather than a missing feature, and saying
	 *                               which invoice is the only way anybody can go and look at it
	 */
	public static LateFineRule ruleFor(Invoice invoice, Map<String, LateFineRule> rules) {
		String structureId = invoice.getStructureId();
		if (structureId == null) {
			if (invoice.resolvedKind() != InstallmentKind.CUSTOM) {
				throw new IllegalStateException("Invoice " + invoice.getId() + " ("
						+ invoice.displayLabel() + " for " + invoice.getStudentUniqueId() + ") is a "
						+ invoice.resolvedKind() + " charge with no structureId. Only a CUSTOM charge may "
						+ "have none, so this document is incomplete and its late fine cannot be worked out.");
			}
			return null;
		}
		return rules == null ? null : rules.get(structureId);
	}

	/**
	 * The fine still owed on this invoice, in paise.
	 *
	 * <p>Nothing is charged while anything of the principal is still within the grace period, nothing
	 * is charged once the invoice is settled or cancelled, and whatever has already been collected as
	 * a fine is taken off.
	 *
	 * @param rule  the structure's rule, or null when the school charges nothing for being late.
	 *              Obtain it with {@link #ruleFor}, never with a direct map lookup
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

package com.school.fees.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * One chunk of the year's fees, with its own due date — "Q1", "Term 1", "Jul 2026", "Semester 3",
 * "Admission fee".
 *
 * <p>The name is how an invoice refers back to its installment, and is therefore what makes
 * invoice generation idempotent. Names are unique within a structure, and one that already has
 * invoices cannot be renamed.
 *
 * @param items       the heads charged in this installment; the installment total is their sum
 * @param kind        what sort of charge it is. Null on an installment written before this field
 *                    existed, which reads as {@link InstallmentKind#MONTHLY} — read it through
 *                    {@link #resolvedKind()}
 * @param label       how to show it on a register column or a receipt line, when that differs from the
 *                    name. Null means "use the name", which is the usual case
 * @param periodStart first day of the session this installment covers. Required on a
 *                    {@link InstallmentKind#SEMESTER}, where it cannot be inferred; null and derived
 *                    from the due date's month on a MONTHLY one, and null on a one-off fee, which
 *                    covers no period at all. Read it through {@link #period()}
 * @param periodEnd   last day covered, inclusive. Same rules as {@code periodStart}
 */
public record Installment(String name, LocalDate dueDate, List<StructureItem> items, InstallmentKind kind,
		String label, LocalDate periodStart, LocalDate periodEnd) {

	/** A plain monthly installment with no separate label, which is what every caller before B19 built. */
	public Installment(String name, LocalDate dueDate, List<StructureItem> items) {
		this(name, dueDate, items, InstallmentKind.MONTHLY, null, null, null);
	}

	/** One with no explicit period — a month, which derives it, or a one-off fee, which has none. */
	public Installment(String name, LocalDate dueDate, List<StructureItem> items, InstallmentKind kind,
			String label) {
		this(name, dueDate, items, kind, label, null, null);
	}

	/** What this installment charges before any concession, in paise, for a section with no override. */
	public long total() {
		return items == null ? 0L : items.stream().mapToLong(StructureItem::amount).sum();
	}

	/** The same for one section, applying each item's override where it has one. */
	public long totalFor(String section) {
		return items == null ? 0L : items.stream().mapToLong(item -> item.amountFor(section)).sum();
	}

	/** The kind, where a missing one means MONTHLY. See {@link InstallmentKind}. */
	public InstallmentKind resolvedKind() {
		return kind == null ? InstallmentKind.MONTHLY : kind;
	}

	/** What to display: the label if one was set, otherwise the name. Never null. */
	public String displayLabel() {
		return label == null || label.isBlank() ? name : label;
	}

	/**
	 * The stretch of the session this covers, or null when it covers none.
	 *
	 * <p>Stored if it was stored. Otherwise <strong>backfilled for a MONTHLY installment</strong> from
	 * its due date — the whole of that calendar month — which is what makes every monthly plan written
	 * before B19 read with a period and no migration. A one-off fee has no period, and a SEMESTER
	 * without one is a document written by hand; both answer null.
	 */
	public FeePeriod period() {
		if (periodStart != null && periodEnd != null) {
			return new FeePeriod(periodStart, periodEnd);
		}
		if (resolvedKind() == InstallmentKind.MONTHLY && dueDate != null) {
			return FeePeriod.ofMonthContaining(dueDate);
		}
		return null;
	}

	/** First day covered, backfilled as {@link #period()} describes. Null when it covers no period. */
	public LocalDate resolvedPeriodStart() {
		FeePeriod period = period();
		return period == null ? null : period.start();
	}

	/** Last day covered, inclusive, backfilled as {@link #period()} describes. */
	public LocalDate resolvedPeriodEnd() {
		FeePeriod period = period();
		return period == null ? null : period.end();
	}

	/**
	 * The date a register orders this column by: the start of its period, falling back to the due date
	 * for an installment that covers no period. Null only when it has neither.
	 */
	public LocalDate orderingDate() {
		LocalDate start = resolvedPeriodStart();
		return start == null ? dueDate : start;
	}
}

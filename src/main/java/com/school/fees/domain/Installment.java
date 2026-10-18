package com.school.fees.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * One chunk of the year's fees, with its own due date — "Q1", "Term 1", "Jul 2026", "Admission fee".
 *
 * <p>The name is how an invoice refers back to its installment, and is therefore what makes
 * invoice generation idempotent. Names are unique within a structure, and one that already has
 * invoices cannot be renamed.
 *
 * @param items the heads charged in this installment; the installment total is their sum
 * @param kind  what sort of charge it is. Null on an installment written before this field existed,
 *              which reads as {@link InstallmentKind#MONTHLY} — read it through {@link #resolvedKind()}
 * @param label how to show it on a register column or a receipt line, when that differs from the
 *              name. Null means "use the name", which is the usual case
 */
public record Installment(String name, LocalDate dueDate, List<StructureItem> items, InstallmentKind kind,
		String label) {

	/** A plain monthly installment with no separate label, which is what every caller before B19 built. */
	public Installment(String name, LocalDate dueDate, List<StructureItem> items) {
		this(name, dueDate, items, InstallmentKind.MONTHLY, null);
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
}

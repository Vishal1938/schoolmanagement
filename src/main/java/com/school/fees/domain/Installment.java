package com.school.fees.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * One chunk of the year's fees, with its own due date — "Q1", "Term 1", "April".
 *
 * <p>The name is how an invoice refers back to its installment, and is therefore what makes
 * invoice generation idempotent. Names are unique within a structure, and one that already has
 * invoices cannot be renamed.
 *
 * @param items the heads charged in this installment; the installment total is their sum
 */
public record Installment(String name, LocalDate dueDate, List<StructureItem> items) {

	/** What this installment charges before any concession, in paise. */
	public long total() {
		return items == null ? 0L : items.stream().mapToLong(StructureItem::amount).sum();
	}
}

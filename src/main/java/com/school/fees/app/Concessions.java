package com.school.fees.app;

import java.util.List;

import com.school.fees.domain.Concession;
import com.school.fees.domain.ConcessionType;
import com.school.fees.domain.InvoiceItem;

/**
 * How a student's concessions reduce one invoice.
 *
 * <p>Pure and static, so the figure an invoice is generated with and the figure
 * {@code POST /fees/concessions/{id}/apply} recalculates are the same arithmetic rather than two
 * implementations that can drift.
 */
public final class Concessions {

	/**
	 * Total reduction for these items, in paise.
	 *
	 * <p>Each concession is measured against only the items it covers: a 50% concession on Transport
	 * takes half the bus fare, not half the bill. Several concessions add up, and the total is capped
	 * at the gross — a family is never billed a negative amount, however generously the waivers were
	 * stacked.
	 *
	 * <p>PERCENT divides with integer arithmetic and so rounds <strong>down</strong>, in the school's
	 * favour by at most a paisa per item. Money is {@code long} paise throughout (CLAUDE.md rule 3);
	 * there is no rounding mode to get wrong because there are no fractions.
	 */
	public static long amountFor(List<InvoiceItem> items, List<Concession> concessions) {
		if (items == null || items.isEmpty() || concessions == null || concessions.isEmpty()) {
			return 0L;
		}
		long gross = items.stream().mapToLong(InvoiceItem::amount).sum();
		long total = 0L;
		for (Concession concession : concessions) {
			total += amountFor(items, concession);
		}
		return Math.min(total, gross);
	}

	private static long amountFor(List<InvoiceItem> items, Concession concession) {
		long base = items.stream()
				.filter(item -> covers(concession, item.headId()))
				.mapToLong(InvoiceItem::amount)
				.sum();
		if (base <= 0L) {
			return 0L;
		}
		if (concession.getType() == ConcessionType.PERCENT) {
			return base * Math.min(100L, concession.getValue()) / 100L;
		}
		// A fixed concession larger than what it covers takes all of it and no more, rather than
		// spilling over onto heads it was not granted against.
		return Math.min(concession.getValue(), base);
	}

	/** An empty {@code headIds} covers every head — see {@link Concession#getHeadIds()}. */
	private static boolean covers(Concession concession, String headId) {
		List<String> headIds = concession.getHeadIds();
		return headIds == null || headIds.isEmpty() || headIds.contains(headId);
	}

	private Concessions() {
	}
}

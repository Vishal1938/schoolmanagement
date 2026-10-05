package com.school.fees.app;

import java.time.LocalDate;
import java.util.List;

/**
 * How much of one payment landed on one invoice, as the fees module reports it back to whoever took
 * the money.
 *
 * <p>This is the whole of what crosses the boundary: the payment module gets the figures it has to
 * record and print, and never an {@code Invoice}. It is also the shape stored on the payment, so a
 * receipt and a collection report are sums of numbers that were fixed at the moment the money was
 * taken, rather than re-derived from invoices that may have been edited since.
 *
 * @param principal the part applied to what was billed, in paise
 * @param lateFine  the part applied to the late fine, in paise. Taken first, so a payment that only
 *                  covers the fine leaves the principal untouched
 * @param heads     the principal split across the invoice's heads, for the "by head" report. Always
 *                  sums exactly to {@link #principal}
 */
public record InvoiceAllocation(
		String invoiceId,
		String installmentName,
		LocalDate dueDate,
		long principal,
		long lateFine,
		List<HeadShare> heads) {

	/** One head's share of the principal collected. */
	public record HeadShare(String headId, String headName, long amount) {
	}

	/** What this allocation came to in total, in paise. */
	public long total() {
		return principal + lateFine;
	}
}

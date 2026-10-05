package com.school.payment.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * How much of one payment landed on one invoice. All amounts are paise.
 *
 * <p>{@code installmentName} and {@code dueDate} are copied in alongside the id, so a receipt can be
 * reprinted years later from the payment alone — reading the invoice back would show what it says
 * today, not what was paid for.
 *
 * @param amount   the part applied to the principal
 * @param lateFine the part applied to the late fine. Settled first, so a part payment never leaves a
 *                 fine quietly growing on an invoice the family thought they had dealt with
 * @param heads    the principal split across the invoice's heads; sums exactly to {@code amount}
 */
public record PaymentAllocation(
		String invoiceId,
		String installmentName,
		LocalDate dueDate,
		long amount,
		long lateFine,
		List<HeadAllocation> heads) {

	/** What landed on this invoice in total, in paise. */
	public long total() {
		return amount + lateFine;
	}
}

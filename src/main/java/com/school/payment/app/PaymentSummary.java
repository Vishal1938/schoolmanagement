package com.school.payment.app;

import java.time.Instant;

import com.school.payment.domain.PaymentMode;

/**
 * One receipt reduced to the line a dashboard prints, as other modules see it.
 *
 * <p>No allocations and no payer details: a feed of recent takings is read to see that money is
 * arriving, and which invoices it landed on is a question for the receipt itself. A module that
 * cannot see the breakdown cannot leak it.
 *
 * @param amount      received, in paise
 * @param studentName null if the student record has since been removed; the id always stands
 */
public record PaymentSummary(
		String id,
		String receiptNo,
		String studentUniqueId,
		String studentName,
		long amount,
		PaymentMode mode,
		Instant paidAt) {
}

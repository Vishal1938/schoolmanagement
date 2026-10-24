package com.school.payment.app;

import java.time.Instant;

import com.school.payment.domain.PaymentMode;

/**
 * Money has actually been received against a student's invoices — at the counter or through the
 * gateway.
 *
 * <p>Published from the two places a payment reaches {@code CAPTURED}, {@link PaymentService#record}
 * and {@link PaymentSettlement#capture}, which is what lets a single listener book both without
 * knowing how the money arrived.
 *
 * <p>The event carries what a reader needs and not the document: a payment holds allocations, a
 * receipt number and a payer, and handing all of it to every listener would make this module's
 * storage shape part of its contract. CLAUDE.md requires cross-module side effects to go through an
 * event rather than a call, and the accounts module (B21) is the subscriber.
 *
 * @param paymentId the payment row, which is also the idempotency key the accounts entry is written
 *                  under: the same payment can be captured once only, so the same entry is written
 *                  once only
 * @param amount    what was received, in paise
 * @param paidAt    when the money moved, which is not always when the row was written
 */
public record PaymentCapturedEvent(
		String paymentId,
		String receiptNo,
		String studentUniqueId,
		String payerName,
		long amount,
		PaymentMode mode,
		Instant paidAt) {
}

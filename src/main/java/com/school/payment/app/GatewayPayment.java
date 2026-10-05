package com.school.payment.app;

/**
 * One attempt to pay a {@link GatewayOrder}, as the gateway reports it.
 *
 * <p>An order can have several of these — a card declined, then a UPI collect that worked — so this
 * is never assumed to be unique per order. {@link #id} is, and it is what the unique index on
 * {@code gatewayPaymentId} keys the settlement off.
 *
 * @param amount           what the gateway actually took, in paise. Read from the gateway rather
 *                         than from our order, because the money that moved is the money that moved
 * @param errorDescription the gateway's reason for a {@link GatewayPaymentStatus#FAILED} attempt,
 *                         safe to show a family. Null otherwise
 */
public record GatewayPayment(
		String id,
		String orderId,
		long amount,
		GatewayPaymentStatus status,
		String method,
		String errorDescription) {
}

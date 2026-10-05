package com.school.payment.domain;

/**
 * How the money arrived.
 *
 * <p>The four offline modes are what a school counter actually takes; {@link #ONLINE} is Razorpay
 * (B12). They share one collection and one receipt format on purpose — a family that paid by cheque
 * and one that paid by card should get the same document, and the collection report should add them
 * up without a special case.
 */
public enum PaymentMode {

	CASH,

	/** {@code reference} is the cheque number. */
	CHEQUE,

	/** UPI paid at the counter rather than through the gateway; {@code reference} is the UPI ref. */
	UPI_OFFLINE,

	/** {@code reference} is the UTR. */
	BANK_TRANSFER,

	/** Through the payment gateway (B12). */
	ONLINE
}

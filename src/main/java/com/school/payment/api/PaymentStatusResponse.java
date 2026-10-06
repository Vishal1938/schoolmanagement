package com.school.payment.api;

import com.school.payment.domain.Payment;
import com.school.payment.domain.PaymentStatus;

/**
 * Where one payment stands — the answer to both {@code POST /payments/verify} and
 * {@code GET /payments/{id}}.
 *
 * <p>Deliberately tiny, because its job is to be polled. A client that has just closed the checkout
 * asks for this until {@link #status} settles; the full {@link PaymentResponse} with its allocations
 * is a separate read, and the receipt is a separate download.
 *
 * @param receiptNo      assigned at capture, so null for anything still {@code CREATED} or
 *                       {@code FAILED}. Its arrival is what tells the client the money is in
 * @param receiptUrl     this API's own download path for the receipt PDF, or null before capture
 * @param failureReason  the gateway's reason, safe to show a family. Null unless {@code FAILED}
 */
public record PaymentStatusResponse(
		String paymentId,
		PaymentStatus status,
		String receiptNo,
		String receiptUrl,
		String failureReason) {

	public static PaymentStatusResponse of(Payment payment, String receiptUrl) {
		return new PaymentStatusResponse(payment.getId(), payment.getStatus(), payment.getReceiptNo(),
				receiptUrl, payment.getFailureReason());
	}
}

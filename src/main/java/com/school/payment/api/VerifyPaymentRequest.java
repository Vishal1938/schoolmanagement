package com.school.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /payments/verify}: the three values the gateway's checkout hands back to the
 * browser when a payment succeeds.
 *
 * <p>None of them is trusted on its own. {@link #razorpaySignature} is an HMAC-SHA256 of
 * {@code orderId|paymentId} under the key secret, which only the gateway and this server can compute;
 * it is checked first, and then the payment's real status is read from the gateway rather than taken
 * from this request. A forged body therefore buys nothing.
 *
 * <p>This endpoint exists for speed, not for truth — it lets the page show a receipt the moment the
 * checkout closes instead of waiting for the webhook. The webhook and the reconciliation job settle
 * the same payment through the same code, so a family that closes the tab too early is not left
 * unpaid.
 */
public record VerifyPaymentRequest(
		@NotBlank @Size(max = 120) String razorpayOrderId,
		@NotBlank @Size(max = 120) String razorpayPaymentId,
		@NotBlank @Size(max = 256) String razorpaySignature) {
}

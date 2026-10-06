package com.school.payment.api;

/**
 * Everything the frontend needs to open the gateway checkout, and nothing more.
 *
 * <p>{@link #keyId} is the gateway's <em>publishable</em> key. The key secret and the webhook secret
 * never leave the server, and no response in this API contains either.
 *
 * @param paymentId our own payment id. Poll {@code GET /payments/{id}} with it after the checkout
 *                  closes; it is also what {@code GET /payments/{id}/receipt.pdf} takes
 * @param orderId   the gateway's order id, which the checkout is opened against
 * @param amount    what the family will be charged, in paise, computed on the server from the
 *                  invoices. The checkout will not accept any other figure for this order
 * @param currency  always {@code INR}
 * @param name      the school's name, for the checkout's heading. From configuration, never hardcoded
 * @param prefill   contact details so a parent is not retyping their own phone number on a card form
 */
public record CreateOrderResponse(
		String paymentId,
		String orderId,
		long amount,
		String currency,
		String keyId,
		String name,
		Prefill prefill) {

	/**
	 * What the checkout pre-fills. Any field may be null — the checkout simply asks for it instead.
	 *
	 * @param name    the payer's name as they gave it on this request
	 * @param email   the family's email from the student's record
	 * @param contact the family's phone number
	 */
	public record Prefill(String name, String email, String contact) {
	}
}

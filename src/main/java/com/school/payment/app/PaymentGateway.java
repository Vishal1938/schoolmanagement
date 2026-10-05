package com.school.payment.app;

import java.util.List;
import java.util.Map;

/**
 * The payment gateway, as this module needs it.
 *
 * <p>An interface rather than the SDK directly, for the same reason
 * {@link com.school.common.storage.ObjectStorage} wraps the S3 client: the services above it make
 * decisions about money, and those decisions should read in our own vocabulary — "is this authorised",
 * "capture it", "did this signature verify" — not in a vendor's. {@link com.school.payment.infra}
 * holds the Razorpay implementation, and it is the only place the SDK is mentioned.
 *
 * <p><strong>Nothing here ever logs or returns a key secret or a signature.</strong> The verification
 * methods answer yes or no; a caller that needs to explain a refusal says that the signature did not
 * verify, never what was compared.
 */
public interface PaymentGateway {

	/** All amounts in this interface are paise, and this is the only currency the deployment uses. */
	String CURRENCY = "INR";

	/**
	 * Whether this deployment has gateway credentials at all. A school that takes no online payments
	 * leaves them unset, and every endpoint that needs them answers 503 rather than failing obscurely.
	 */
	boolean isConfigured();

	/** The public key id, which the frontend needs to open the checkout. Never the secret. */
	String keyId();

	/**
	 * Creates an order for exactly {@code amountPaise}.
	 *
	 * @param receipt our own payment id, so an entry in the gateway dashboard can be traced back
	 * @param notes   short key/value pairs stored against the order at the gateway. Reconciliation
	 *                aids only — never anything a family would mind a support agent reading
	 */
	GatewayOrder createOrder(long amountPaise, String receipt, Map<String, String> notes);

	/** One payment attempt, by the gateway's payment id. */
	GatewayPayment fetchPayment(String gatewayPaymentId);

	/** Every attempt made against one order, which is how a stalled order is reconciled. */
	List<GatewayPayment> paymentsForOrder(String gatewayOrderId);

	/**
	 * Moves an authorised payment's money. Idempotent at the gateway: capturing a captured payment
	 * returns it unchanged rather than charging twice.
	 *
	 * @param amountPaise must equal what was authorised
	 */
	GatewayPayment capture(String gatewayPaymentId, long amountPaise);

	/**
	 * Whether the checkout handshake is genuine: HMAC-SHA256 of {@code orderId|paymentId} under the
	 * key secret, compared in constant time.
	 *
	 * @return false for a signature that does not match, and for one that is malformed. A caller must
	 *         credit nothing on false
	 */
	boolean verifyPaymentSignature(String gatewayOrderId, String gatewayPaymentId, String signature);

	/**
	 * Whether an inbound webhook is genuine: HMAC-SHA256 of the <em>raw</em> request body under the
	 * webhook secret, which is a different secret from the key secret.
	 *
	 * @param payload the body exactly as it arrived. Re-serialising parsed JSON changes the bytes and
	 *                the signature will not match
	 */
	boolean verifyWebhookSignature(String payload, String signature);
}

package com.school.payment.infra;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.razorpay.Entity;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.school.common.config.AppProperties;
import com.school.common.exceptions.AppException;
import com.school.common.exceptions.ErrorType;
import com.school.payment.app.GatewayOrder;
import com.school.payment.app.GatewayPayment;
import com.school.payment.app.GatewayPaymentStatus;
import com.school.payment.app.PaymentGateway;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Razorpay, behind {@link PaymentGateway}. The only class in the application that imports the SDK.
 *
 * <p><strong>Secrets never leave this class and are never logged.</strong> The key secret and the
 * webhook secret are read from configuration, handed to the SDK and used for HMAC comparison; no log
 * statement here interpolates them, nor any signature, nor the raw webhook body. What is logged is
 * the order id, the payment id and the status — identifiers a support conversation needs and that
 * grant nothing on their own.
 *
 * <p>Every SDK failure becomes a {@link ErrorType#DEPENDENCY_FAILED} (502) rather than a 500: the
 * request was fine, the upstream was not, and the client should be told which.
 */
@Component
public class RazorpayGateway implements PaymentGateway {

	private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);

	private final AppProperties.Razorpay config;

	/** Null when this deployment has no credentials. Thread-safe and reusable, so built once. */
	private final RazorpayClient client;

	public RazorpayGateway(AppProperties properties) {
		this.config = properties.razorpay();
		this.client = buildClient(config);
		if (client == null) {
			log.info("Razorpay is not configured; online payment endpoints will answer 503. "
					+ "Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET to enable them.");
		}
	}

	private static RazorpayClient buildClient(AppProperties.Razorpay config) {
		if (isBlank(config.keyId()) || isBlank(config.keySecret())) {
			return null;
		}
		try {
			return new RazorpayClient(config.keyId(), config.keySecret());
		}
		catch (RazorpayException ex) {
			// The constructor only validates the credentials' shape, so this is a misconfiguration
			// rather than an outage. The message is the SDK's and names no secret.
			throw new IllegalStateException("Razorpay credentials were rejected: " + ex.getMessage(), ex);
		}
	}

	@Override
	public boolean isConfigured() {
		return client != null;
	}

	@Override
	public String keyId() {
		return config.keyId();
	}

	// --- orders and payments ----------------------------------------------------------------------

	@Override
	public GatewayOrder createOrder(long amountPaise, String receipt, Map<String, String> notes) {
		JSONObject request = new JSONObject();
		request.put("amount", amountPaise);
		request.put("currency", CURRENCY);
		request.put("receipt", receipt);
		if (notes != null && !notes.isEmpty()) {
			request.put("notes", new JSONObject(notes));
		}
		// payment_capture is deliberately not sent: whether an order auto-captures is an account-level
		// setting at Razorpay, and the settlement code handles "authorized" and "captured" alike. One
		// less thing that has to agree between this file and a dashboard nobody here can see.
		JSONObject order = call("create order", () -> client.orders.create(request));
		log.info("Created Razorpay order {} for {} paise against payment {}",
				order.optString("id", null), amountPaise, receipt);
		return new GatewayOrder(order.optString("id", null), order.optLong("amount"),
				order.optString("currency", CURRENCY), order.optString("receipt", null));
	}

	@Override
	public GatewayPayment fetchPayment(String gatewayPaymentId) {
		return toPayment(call("fetch payment", () -> client.payments.fetch(gatewayPaymentId)));
	}

	@Override
	public List<GatewayPayment> paymentsForOrder(String gatewayOrderId) {
		List<com.razorpay.Payment> attempts = callRaw("fetch the payments for an order",
				() -> client.orders.fetchPayments(gatewayOrderId));
		List<GatewayPayment> payments = new ArrayList<>(attempts.size());
		for (com.razorpay.Payment attempt : attempts) {
			payments.add(toPayment(attempt.toJson()));
		}
		return payments;
	}

	@Override
	public GatewayPayment capture(String gatewayPaymentId, long amountPaise) {
		JSONObject request = new JSONObject();
		request.put("amount", amountPaise);
		request.put("currency", CURRENCY);
		GatewayPayment captured = toPayment(
				call("capture payment", () -> client.payments.capture(gatewayPaymentId, request)));
		log.info("Captured Razorpay payment {} for {} paise; status is now {}",
				gatewayPaymentId, amountPaise, captured.status());
		return captured;
	}

	// --- signatures -------------------------------------------------------------------------------

	@Override
	public boolean verifyPaymentSignature(String gatewayOrderId, String gatewayPaymentId, String signature) {
		if (isBlank(gatewayOrderId) || isBlank(gatewayPaymentId) || isBlank(signature)) {
			return false;
		}
		JSONObject attributes = new JSONObject();
		attributes.put("razorpay_order_id", gatewayOrderId);
		attributes.put("razorpay_payment_id", gatewayPaymentId);
		attributes.put("razorpay_signature", signature);
		// The SDK hashes orderId|paymentId with the key secret and compares with MessageDigest.isEqual,
		// so the comparison is constant-time. Reused rather than reimplemented for exactly that reason.
		return verified(() -> Utils.verifyPaymentSignature(attributes, config.keySecret()),
				"payment signature for order " + gatewayOrderId);
	}

	@Override
	public boolean verifyWebhookSignature(String payload, String signature) {
		if (isBlank(config.webhookSecret())) {
			// Refusing is the safe default: without the secret there is no way to tell Razorpay's call
			// from anyone else's, and this endpoint is unauthenticated.
			log.warn("A payment webhook arrived but RAZORPAY_WEBHOOK_SECRET is not set; rejecting it.");
			return false;
		}
		if (isBlank(payload) || isBlank(signature)) {
			return false;
		}
		return verified(() -> Utils.verifyWebhookSignature(payload, signature, config.webhookSecret()),
				"webhook signature");
	}

	/**
	 * Runs a verification, treating a thrown exception as "did not verify".
	 *
	 * <p>The SDK throws on a signature it cannot even parse, which is a failed verification and not an
	 * outage — the one caller that reaches this with garbage is an unauthenticated endpoint, and a
	 * forged request should be refused rather than turned into a 502. Nothing about the comparison is
	 * logged, only which check it was.
	 */
	private boolean verified(VerificationCall call, String what) {
		try {
			return call.verify();
		}
		catch (RazorpayException ex) {
			log.debug("Could not verify the {}: {}", what, ex.getMessage());
			return false;
		}
	}

	// --- internals --------------------------------------------------------------------------------

	private static GatewayPayment toPayment(JSONObject payment) {
		return new GatewayPayment(
				payment.optString("id", null),
				payment.optString("order_id", null),
				payment.optLong("amount"),
				GatewayPaymentStatus.of(payment.optString("status", null)),
				payment.optString("method", null),
				payment.optString("error_description", null));
	}

	/**
	 * Calls the SDK, turning its checked exception into a 502 and its entity into plain JSON.
	 *
	 * <p>Reading fields off {@link Entity#toJson()} rather than through {@code Entity.get(key)}, whose
	 * unchecked generic cast makes {@code long amount = order.get("amount")} compile and then throw at
	 * runtime because the JSON parser handed back an {@code Integer}. {@code optLong} cannot do that.
	 */
	private <T extends Entity> JSONObject call(String what, SdkCall<T> sdkCall) {
		return callRaw(what, sdkCall).toJson();
	}

	private <T> T callRaw(String what, SdkCall<T> sdkCall) {
		if (!isConfigured()) {
			throw new AppException(ErrorType.FEATURE_DISABLED,
					"Online payment is not configured for this school. Pay at the school office, or ask an "
							+ "administrator to configure the payment gateway.");
		}
		try {
			return sdkCall.call();
		}
		catch (RazorpayException ex) {
			// The SDK's message carries the gateway's own error description, which is client-safe and
			// never includes credentials.
			log.warn("Razorpay could not {}: {}", what, ex.getMessage());
			throw new AppException(ErrorType.DEPENDENCY_FAILED,
					"The payment gateway could not " + what + ": " + ex.getMessage(), ex);
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	@FunctionalInterface
	private interface SdkCall<T> {

		T call() throws RazorpayException;
	}

	@FunctionalInterface
	private interface VerificationCall {

		boolean verify() throws RazorpayException;
	}
}

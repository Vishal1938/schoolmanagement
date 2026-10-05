package com.school.payment.api;

import java.util.Map;

import com.school.payment.app.OnlinePaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The payment gateway's callback. The authority on whether a payment happened.
 *
 * <p>Unauthenticated, like everything under {@code /public/**} — the gateway has no login here and
 * cannot be given one. <strong>The signature is what authenticates it.</strong> An HMAC-SHA256 of the
 * raw body under {@code RAZORPAY_WEBHOOK_SECRET} arrives in {@code X-Razorpay-Signature}, and a body
 * that does not verify is refused with 403 before anything is parsed or written. A deployment with no
 * webhook secret configured refuses every call rather than trusting them all.
 *
 * <p>The body is taken as a {@code String} on purpose. The signature covers the exact bytes that were
 * sent, so letting Jackson parse it into an object and re-serialising it to check would change the
 * whitespace and fail every time.
 */
@RestController
@RequestMapping("/public/payments")
@Tag(name = "Public", description = "Endpoints an anonymous visitor may call")
public class PublicPaymentWebhookController {

	private final OnlinePaymentService online;

	public PublicPaymentWebhookController(OnlinePaymentService online) {
		this.online = online;
	}

	/**
	 * Always answers 200 for anything it has handled or deliberately ignored — an unrecognised event,
	 * an order belonging to something else, a payment already settled. The gateway redelivers on any
	 * non-2xx, so a 200 is how "there is nothing to do here" is said without starting a retry loop.
	 * Only a bad signature (403) and a genuine failure (5xx, worth retrying) break that.
	 */
	@PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.OK)
	@Operation(summary = "Razorpay payment webhook",
			description = "Verifies X-Razorpay-Signature against the raw body with RAZORPAY_WEBHOOK_SECRET, then "
					+ "handles payment.captured (settles the payment, allocates it to the invoices and issues the "
					+ "receipt) and payment.failed. Every other event is ignored with a 200. Idempotent: a "
					+ "redelivered event settles nothing twice. Not for clients to call.")
	public Map<String, String> webhook(
			@RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
			@RequestBody String payload) {
		online.handleWebhook(payload, signature);
		return Map.of("status", "ok");
	}
}

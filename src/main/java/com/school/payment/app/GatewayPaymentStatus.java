package com.school.payment.app;

import java.util.Locale;

/**
 * Where a payment stands <em>at the gateway</em>, which is not the same question as
 * {@link com.school.payment.domain.PaymentStatus}: that one is where it stands in our books.
 *
 * <p>Deliberately our own enum rather than the gateway's raw string. The settlement decision —
 * capture it, settle it, fail it — is made by switching on this, and a {@code switch} over an enum is
 * exhaustive where a chain of string comparisons silently falls through on a value the gateway adds
 * later. That is what {@link #UNKNOWN} is for.
 */
public enum GatewayPaymentStatus {

	/** The checkout is open or the authorisation is still in flight. Nothing to do yet. */
	CREATED,

	/** The bank has held the money but not moved it. We capture it, then settle. */
	AUTHORIZED,

	/** The money has moved. This is what we settle against. */
	CAPTURED,

	/** Captured and then given back. The invoices must not be credited. */
	REFUNDED,

	FAILED,

	/** A status this version does not know. Treated as "not resolved yet", never as money received. */
	UNKNOWN;

	/** Maps Razorpay's {@code status} field. An unrecognised value is {@link #UNKNOWN}, never a guess. */
	public static GatewayPaymentStatus of(String raw) {
		if (raw == null || raw.isBlank()) {
			return UNKNOWN;
		}
		return switch (raw.toLowerCase(Locale.ROOT)) {
			case "created", "pending" -> CREATED;
			case "authorized" -> AUTHORIZED;
			case "captured" -> CAPTURED;
			case "refunded" -> REFUNDED;
			case "failed" -> FAILED;
			default -> UNKNOWN;
		};
	}
}

package com.school.payment.domain;

/**
 * Where a payment stands.
 *
 * <p>An offline payment is {@link #CAPTURED} from the moment it is recorded: the clerk has the cash
 * in hand, so there is nothing to wait for. The intermediate states exist for the gateway flow in
 * B12, where an order is created before anyone has paid anything.
 */
public enum PaymentStatus {

	/** An order exists but no money has moved. Only reachable through the gateway flow (B12). */
	CREATED,

	/** The money is in. This is the only status that has a receipt number and moves an invoice. */
	CAPTURED,

	FAILED
}

package com.school.payment.domain;

import java.time.Instant;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Money received against a student's invoices, however it arrived.
 *
 * <p><strong>One collection for counter payments and gateway payments.</strong> The gateway's order
 * and payment ids and the {@code CREATED → CAPTURED} flow are here alongside everything that makes a
 * receipt — the allocations, the payer, the receipt number — so an online payment produces the same
 * document through the same code rather than a parallel one.
 *
 * <p>Payments are append-only. A mistake is corrected by cancelling the invoice or taking a further
 * payment, never by editing one: a receipt has been handed over, and the audit trail has to agree
 * with the paper.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(Payment.COLLECTION)
// A student's payment history, newest first.
@CompoundIndex(name = "payments_student_idx", def = "{'studentUniqueId': 1, 'paidAt': -1}")
// The collection report: captured payments in a date range.
@CompoundIndex(name = "payments_status_paid_at_idx", def = "{'status': 1, 'paidAt': 1}")
// The reconciliation job: gateway orders left CREATED for too long, oldest first.
@CompoundIndex(name = "payments_status_mode_created_at_idx",
		def = "{'status': 1, 'mode': 1, 'createdAt': 1}")
public class Payment {

	public static final String COLLECTION = "payments";

	@Id
	private String id;

	private String studentUniqueId;

	private PaymentMode mode;

	/**
	 * Total received, in paise. Equals the sum of the allocations plus {@link #unallocatedAmount},
	 * which is zero for every payment but the rare online over-collection described there.
	 */
	private long amount;

	/**
	 * The invoices this payment was raised against, as selected.
	 *
	 * <p>Needed because a gateway order is created minutes before it is settled: the selection has to
	 * survive in between, and the webhook that settles it arrives with nothing but an order id. Kept
	 * for counter payments too, where it records what the clerk picked even if an invoice ended up
	 * taking nothing.
	 */
	private List<String> invoiceIds;

	/** Where it went. Empty only for a gateway order that was never captured. */
	private List<PaymentAllocation> allocations;

	/**
	 * Money taken that no invoice needed, in paise. Null — not zero — for the normal case, so it is
	 * absent from the API and from the trail unless it actually happened.
	 *
	 * <p>Only reachable online, and only in a narrow window: the amount is fixed when the order is
	 * created, and between then and the capture a clerk can take the same invoices at the counter or an
	 * admin can apply a concession. The money is in, so the payment is still {@code CAPTURED}; this is
	 * the part of it that is owed back. It is audited loudly when it is written, because somebody has
	 * to decide on a refund.
	 */
	private Long unallocatedAmount;

	/** Cheque number, UTR, UPI reference — whatever identifies the transfer. Null for cash. */
	private String reference;

	/**
	 * The person who handed the money over, as they gave their name. Printed on the receipt.
	 *
	 * <p>Not a user account and never matched against one: there are no parent logins in this system,
	 * so this is the only record of who actually paid.
	 */
	private String payerName;

	private PayerRelation payerRelation;

	/**
	 * {@code {receiptPrefix}-{YY}-{SEQ}}, e.g. {@code DVM-RCP-26-000001}. Unique and sparse: assigned
	 * when a payment is captured, so a gateway order waiting to be paid has none yet, and many such
	 * nulls must not collide.
	 */
	@Indexed(name = "payments_receipt_no_idx", unique = true, sparse = true)
	private String receiptNo;

	/**
	 * The gateway's order id. Null for a counter payment.
	 *
	 * <p>Unique and sparse, and the lookup key for both the verify call and the webhook — neither
	 * knows our payment id, only the order they were handed.
	 */
	@Indexed(name = "payments_gateway_order_idx", unique = true, sparse = true)
	private String gatewayOrderId;

	/**
	 * The gateway's id for the attempt that actually paid. Null until capture, and for counter
	 * payments.
	 *
	 * <p><strong>The unique index is the idempotency guard.</strong> A webhook can be delivered twice,
	 * and the verify call, the webhook and the reconciliation job can all reach the same payment at
	 * once; the settlement re-reads the status inside its transaction, and this index is what stops two
	 * <em>different</em> payment rows from ever claiming the same money at the gateway.
	 */
	@Indexed(name = "payments_gateway_payment_idx", unique = true, sparse = true)
	private String gatewayPaymentId;

	private PaymentStatus status;

	/**
	 * Why a gateway payment failed, in the gateway's words — safe to show a family. Null unless the
	 * status is {@link PaymentStatus#FAILED}.
	 */
	private String failureReason;

	/** {@code uniqueId} of the clerk who took it. Null for a gateway capture, which nobody recorded. */
	private String recordedBy;

	/**
	 * When the money was received, which is not always when the row was written — a cheque banked on
	 * Monday may be entered on Tuesday. The collection report groups on this.
	 */
	private Instant paidAt;

	private Instant createdAt;

	/** The part of {@link #amount} that settled late fines rather than fees, in paise. */
	public long lateFineTotal() {
		return allocations == null ? 0L
				: allocations.stream().mapToLong(PaymentAllocation::lateFine).sum();
	}

	/** The part of {@link #amount} that settled what was billed, in paise. */
	public long principalTotal() {
		return allocations == null ? 0L
				: allocations.stream().mapToLong(PaymentAllocation::amount).sum();
	}
}

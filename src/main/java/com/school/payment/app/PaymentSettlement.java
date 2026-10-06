package com.school.payment.app;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.NotFoundException;
import com.school.fees.app.FeeQuote;
import com.school.fees.app.InvoiceAllocation;
import com.school.fees.app.InvoiceService;
import com.school.payment.domain.Payment;
import com.school.payment.domain.PaymentStatus;
import com.school.payment.infra.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional end of a gateway payment: settling one, or marking it failed.
 *
 * <p>A separate class from {@link OnlinePaymentService} purely so the transaction boundary is a real
 * one. Everything that reaches the gateway over HTTP — fetching a payment, capturing it, rendering
 * the receipt — has to happen <em>outside</em> the database transaction, and a {@code @Transactional}
 * method calling another on the same bean would not start one at all.
 *
 * <p><strong>Three callers, one path.</strong> The student's verify call, the webhook and the
 * reconciliation job all settle through {@link #capture}, so there is one definition of what settling
 * means and no chance of the three drifting apart. They also routinely race each other, which is why
 * every method here is written to be safe when it runs second.
 */
@Service
public class PaymentSettlement {

	private static final Logger log = LoggerFactory.getLogger(PaymentSettlement.class);

	private final PaymentRepository payments;
	private final PaymentService paymentService;
	private final InvoiceService invoices;
	private final AuditService audit;

	public PaymentSettlement(PaymentRepository payments, PaymentService paymentService, InvoiceService invoices,
			AuditService audit) {
		this.payments = payments;
		this.paymentService = paymentService;
		this.invoices = invoices;
		this.audit = audit;
	}

	/**
	 * Settles a captured gateway payment: marks it {@code CAPTURED}, stores the gateway's payment id,
	 * allocates the money across the invoices and assigns the receipt number.
	 *
	 * <p><strong>One transaction</strong>, for the same reason the counter flow uses one: a receipt
	 * number with no payment behind it, or invoices marked paid with nobody recorded as having paid
	 * them, are both worse than a failed request. The receipt <em>PDF</em> is rendered by the caller
	 * after this commits — writing to object storage inside a database transaction would orphan a file
	 * on every rollback.
	 *
	 * <p><strong>Idempotent.</strong> Three callers race here and a webhook can be delivered twice, so
	 * a payment that is already {@code CAPTURED} is returned untouched rather than allocated a second
	 * time. The status is re-read inside the transaction, so two callers that both saw {@code CREATED}
	 * a moment ago cannot both get through; the unique index on {@code gatewayPaymentId} catches the
	 * remaining case, two different payment rows claiming one payment at the gateway.
	 *
	 * <p>The amount is <strong>allocated against a fresh quote</strong>, not against the order. Minutes
	 * have passed: the late fine may have grown, or a clerk may have taken the same invoices at the
	 * counter. Whatever the invoices can absorb is allocated; anything left over is recorded as
	 * {@link Payment#getUnallocatedAmount()} and audited, because it is a refund somebody has to
	 * decide on and losing track of it is not an option.
	 *
	 * @param capturedAmount what the gateway says it actually took, in paise — not what we asked for
	 * @param paidAt         when the gateway captured it, which is when the money moved
	 */
	@Transactional
	public Payment capture(String paymentId, String gatewayPaymentId, long capturedAmount, Instant paidAt) {
		Payment before = payments.findById(paymentId)
				.orElseThrow(() -> NotFoundException.of("Payment", paymentId));
		if (before.getStatus() == PaymentStatus.CAPTURED) {
			// Already settled. A duplicate webhook, or verify and the webhook arriving together: both
			// are normal, and the right answer to both is the receipt that already exists.
			log.debug("Payment {} is already captured; nothing to settle", paymentId);
			return before;
		}
		if (before.getStatus() != PaymentStatus.CREATED) {
			throw new ConflictException("Payment " + paymentId + " is " + before.getStatus()
					+ ", so it cannot be settled now. The money held at the gateway needs a refund decision.");
		}

		FeeQuote quote = invoices.requoteForSettlement(before.getStudentUniqueId(), before.getInvoiceIds());
		long allocatable = Math.min(capturedAmount, quote.amountDue());
		List<InvoiceAllocation> applied = allocatable <= 0L
				? List.of()
				: invoices.allocate(before.getStudentUniqueId(), quote.invoiceIds(), allocatable);
		long allocated = applied.stream().mapToLong(InvoiceAllocation::total).sum();
		long unallocated = capturedAmount - allocated;

		Payment settled;
		try {
			settled = payments.save(before.toBuilder()
					.status(PaymentStatus.CAPTURED)
					.gatewayPaymentId(gatewayPaymentId)
					// What the gateway took, which is what the receipt has to say. Normally identical to
					// the order amount this row was created with.
					.amount(capturedAmount)
					.unallocatedAmount(unallocated == 0L ? null : unallocated)
					.allocations(applied.stream().map(PaymentService::toAllocation).toList())
					.receiptNo(paymentService.nextReceiptNo())
					.paidAt(paidAt)
					.build());
		}
		catch (DuplicateKeyException ex) {
			throw new ConflictException("Gateway payment " + gatewayPaymentId
					+ " has already been settled against another payment, so it has not been settled again.", ex);
		}

		audit.record(AuditAction.PAYMENT_CAPTURED, PaymentService.AUDIT_ENTITY, settled.getId(), before, settled);
		if (unallocated > 0L) {
			// Loud on purpose: this is money the school holds and does not have a bill for.
			log.warn("Payment {} captured {} paise but the invoices could only absorb {}; {} paise is owed back",
					settled.getId(), capturedAmount, allocated, unallocated);
			audit.record(AuditAction.PAYMENT_UNALLOCATED, PaymentService.AUDIT_ENTITY, settled.getId(), null,
					Map.of("capturedAmount", capturedAmount, "allocatedAmount", allocated,
							"unallocatedAmount", unallocated, "receiptNo", settled.getReceiptNo()));
		}
		return settled;
	}

	/**
	 * Marks a gateway payment failed, with the gateway's own reason.
	 *
	 * <p>Also idempotent, and it refuses to undo a settlement: a {@code FAILED} event arriving after a
	 * capture means the two sides disagree about whether money moved, and quietly marking a receipted
	 * payment failed would leave invoices paid against a payment that says it never happened.
	 */
	@Transactional
	public Payment fail(String paymentId, String reason) {
		Payment before = payments.findById(paymentId)
				.orElseThrow(() -> NotFoundException.of("Payment", paymentId));
		if (before.getStatus() == PaymentStatus.CAPTURED) {
			throw new ConflictException("Payment " + paymentId + " has already been captured and receipted as "
					+ before.getReceiptNo() + ", so it cannot be marked failed.");
		}
		if (before.getStatus() == PaymentStatus.FAILED) {
			return before;
		}

		Payment failed = payments.save(before.toBuilder()
				.status(PaymentStatus.FAILED)
				.failureReason(reason)
				.build());
		audit.record(AuditAction.PAYMENT_FAILED, PaymentService.AUDIT_ENTITY, failed.getId(), before, failed);
		return failed;
	}
}

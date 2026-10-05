package com.school.payment.app;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.config.AppProperties;
import com.school.common.exceptions.BusinessRuleException;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ForbiddenException;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ValidationException;
import com.school.common.id.IdGenerator;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.CurrentUser;
import com.school.common.security.Permission;
import com.school.fees.app.InvoiceAllocation;
import com.school.fees.app.InvoiceService;
import com.school.payment.api.OfflinePaymentRequest;
import com.school.payment.domain.HeadAllocation;
import com.school.payment.domain.Payment;
import com.school.payment.domain.PaymentAllocation;
import com.school.payment.domain.PaymentMode;
import com.school.payment.domain.PaymentStatus;
import com.school.payment.infra.PaymentRepository;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import com.school.schoolconfig.app.SchoolConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payments: recording one taken at the counter, and reading them back.
 *
 * <p>The invoices are not this module's to write. {@link InvoiceService#allocate} does that and
 * reports back what landed where; this class owns the receipt number, the mode, the payer and the
 * audit entry.
 *
 * <p>{@link PaymentSettlement} settles a gateway payment through the same two steps, reusing
 * {@link #nextReceiptNo()} and {@link #toAllocation} so a card payment and a cheque produce the same
 * document from the same numbering.
 */
@Service
public class PaymentService {

	static final String AUDIT_ENTITY = "Payment";

	/** Digits in the receipt sequence: {@code DVM-RCP-26-000001}. */
	private static final int RECEIPT_SEQUENCE_WIDTH = 6;

	private final PaymentRepository payments;
	private final InvoiceService invoices;
	private final StudentService students;
	private final SchoolConfigService schoolConfig;
	private final IdGenerator idGenerator;
	private final AppProperties properties;
	private final AuditService audit;
	private final Clock clock;

	public PaymentService(PaymentRepository payments, InvoiceService invoices, StudentService students,
			SchoolConfigService schoolConfig, IdGenerator idGenerator, AppProperties properties, AuditService audit,
			Clock clock) {
		this.payments = payments;
		this.invoices = invoices;
		this.students = students;
		this.schoolConfig = schoolConfig;
		this.idGenerator = idGenerator;
		this.properties = properties;
		this.audit = audit;
		this.clock = clock;
	}

	// --- recording --------------------------------------------------------------------------------

	/**
	 * Records money taken at the counter and settles the invoices it pays for.
	 *
	 * <p><strong>One transaction.</strong> The invoice updates, the receipt number and the payment row
	 * either all land or none do: a receipt number handed to a family with no payment behind it, or
	 * invoices marked paid with no record of who paid, are both worse than a failed request. The
	 * counter increment joins the same transaction, so a rollback hands the receipt number back and
	 * the next payment reuses it — which is why MongoDB has to run as a replica set even locally.
	 *
	 * <p>The receipt PDF is deliberately <em>not</em> rendered here. Writing to object storage inside a
	 * database transaction would leave an orphaned file whenever the transaction rolled back, and
	 * would make taking a payment wait on the store being up. It is rendered on first download
	 * instead, and cached from then on — see {@link ReceiptService}.
	 */
	@Transactional
	public Payment record(OfflinePaymentRequest request) {
		if (request.mode() == PaymentMode.ONLINE) {
			throw new ValidationException("ONLINE is not a counter payment",
					List.of(new FieldViolation("mode", "must be CASH, CHEQUE, UPI_OFFLINE or BANK_TRANSFER; "
							+ "online payments go through the gateway")));
		}
		StudentRef student = students.requireRef(request.studentUniqueId());
		requireReference(request);

		List<InvoiceAllocation> applied = invoices.allocate(student.uniqueId(), request.invoiceIds(),
				request.amount());
		Instant now = Instant.now(clock);
		AuthPrincipal clerk = CurrentUser.require();

		Payment saved = payments.insert(Payment.builder()
				.studentUniqueId(student.uniqueId())
				.mode(request.mode())
				.amount(request.amount())
				.invoiceIds(List.copyOf(request.invoiceIds()))
				.allocations(applied.stream().map(PaymentService::toAllocation).toList())
				.reference(trimToNull(request.reference()))
				.payerName(request.payerName().trim())
				.payerRelation(request.payerRelation())
				.receiptNo(nextReceiptNo())
				// In hand at the counter, so there is nothing to wait for.
				.status(PaymentStatus.CAPTURED)
				.recordedBy(clerk.uniqueId())
				.paidAt(request.paidAt() == null ? now : request.paidAt())
				.createdAt(now)
				.build());

		audit.record(AuditAction.PAYMENT_RECORDED, AUDIT_ENTITY, saved.getId(), null, saved);
		return saved;
	}

	/**
	 * {@code {receiptPrefix}-{YY}-{SEQ}} from the school configuration, e.g. {@code DVM-RCP-26-000001}.
	 * The prefix is configured per deployment, so nothing here is school-specific (CLAUDE.md rule 6).
	 *
	 * <p>Package-private rather than private: {@link PaymentSettlement} numbers a gateway capture, and
	 * a receipt is a receipt however the money arrived. One sequence, one format, one definition.
	 */
	String nextReceiptNo() {
		String prefix = schoolConfig.receiptPrefix();
		if (prefix == null || prefix.isBlank()) {
			throw new BusinessRuleException(
					"No receipt prefix is configured. An administrator must set "
							+ "academicSettings.receiptPrefix before payments can be receipted.");
		}
		return idGenerator.nextNumber(prefix, RECEIPT_SEQUENCE_WIDTH);
	}

	/** Cash has nothing to quote; everything else is a transfer somebody has to be able to trace. */
	private static void requireReference(OfflinePaymentRequest request) {
		if (request.mode() != PaymentMode.CASH && trimToNull(request.reference()) == null) {
			throw new ValidationException("That mode needs a reference",
					List.of(new FieldViolation("reference", "is required for " + request.mode()
							+ " — the cheque number, UTR or UPI reference")));
		}
	}

	// --- reading ----------------------------------------------------------------------------------

	/** One student's payments, newest first. Admin only; a student uses {@link #mine()}. */
	public List<Payment> forStudent(String studentUniqueId) {
		StudentRef student = students.requireRef(studentUniqueId);
		return payments.findByStudentUniqueIdOrderByPaidAtDesc(student.uniqueId());
	}

	/** The caller's own payments. Empty for a login that is not a student's. */
	public List<Payment> mine() {
		return payments.findByStudentUniqueIdOrderByPaidAtDesc(CurrentUser.require().uniqueId());
	}

	/**
	 * One payment, with the object-level check that gates the receipt: an admin, or the student the
	 * payment is for. A teacher is refused — a receipt names amounts (CLAUDE.md rule 2).
	 */
	public Payment requireReadable(String id) {
		Payment payment = payments.findById(id).orElseThrow(() -> NotFoundException.of("Payment", id));
		AuthPrincipal caller = CurrentUser.require();
		boolean self = caller.uniqueId() != null && caller.uniqueId().equals(payment.getStudentUniqueId());
		if (!caller.permissions().contains(Permission.FEE_READ_FULL) && !self) {
			throw new ForbiddenException("You may only read your own payments");
		}
		return payment;
	}

	/** This API's own download path for a receipt, or null for a payment that has no receipt yet. */
	public String receiptUrl(Payment payment) {
		return payment.getReceiptNo() == null
				? null
				: properties.apiBasePath() + "/payments/" + payment.getId() + "/receipt.pdf";
	}

	// --- internals --------------------------------------------------------------------------------

	/**
	 * The fees module's allocation, as this module stores it. Same figures, this module's type.
	 *
	 * <p>Package-private so {@link PaymentSettlement} converts a gateway capture's allocations the same
	 * way a counter payment's are converted.
	 */
	static PaymentAllocation toAllocation(InvoiceAllocation allocation) {
		return new PaymentAllocation(allocation.invoiceId(), allocation.installmentName(), allocation.dueDate(),
				allocation.principal(), allocation.lateFine(),
				allocation.heads().stream()
						.map(head -> new HeadAllocation(head.headId(), head.headName(), head.amount()))
						.toList());
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}

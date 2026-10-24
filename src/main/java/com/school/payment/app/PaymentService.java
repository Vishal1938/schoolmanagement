package com.school.payment.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
import com.school.payment.api.MarkPaidRequest;
import com.school.payment.api.OfflinePaymentRequest;
import com.school.payment.domain.HeadAllocation;
import com.school.payment.domain.PayerRelation;
import com.school.payment.domain.Payment;
import com.school.payment.domain.PaymentAllocation;
import com.school.payment.domain.PaymentMode;
import com.school.payment.domain.PaymentStatus;
import com.school.payment.infra.PaymentRepository;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import com.school.schoolconfig.app.SchoolConfigService;
import org.springframework.context.ApplicationEventPublisher;
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
	private final ApplicationEventPublisher events;
	private final Clock clock;

	public PaymentService(PaymentRepository payments, InvoiceService invoices, StudentService students,
			SchoolConfigService schoolConfig, IdGenerator idGenerator, AppProperties properties, AuditService audit,
			ApplicationEventPublisher events, Clock clock) {
		this.payments = payments;
		this.invoices = invoices;
		this.students = students;
		this.schoolConfig = schoolConfig;
		this.idGenerator = idGenerator;
		this.properties = properties;
		this.audit = audit;
		this.events = events;
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

		// As of the day the money was received, not as of today: a cheque banked on the 25th and
		// entered a fortnight later owes the fine that had accrued by the 25th. Charging today's would
		// take the extra fortnight out of the principal and leave the family short-paid.
		List<InvoiceAllocation> applied = invoices.allocate(student.uniqueId(), request.invoiceIds(),
				request.amount(), fineDateOf(request.paidAt()));
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
		publishCaptured(saved);
		return saved;
	}

	/**
	 * Announces that money was received, for whoever books it — the accounts module (B21).
	 *
	 * <p>Package-private, because {@link PaymentSettlement} publishes the same event for a gateway
	 * capture and the two must not drift. The listeners are {@code AFTER_COMMIT}, so a failure to book
	 * the entry can never roll back the payment itself; what fixes a missed one is
	 * {@code POST /accounts/backfill}, not a failed receipt.
	 */
	void publishCaptured(Payment payment) {
		events.publishEvent(new PaymentCapturedEvent(payment.getId(), payment.getReceiptNo(),
				payment.getStudentUniqueId(), payment.getPayerName(), payment.getAmount(), payment.getMode(),
				payment.getPaidAt()));
	}

	/**
	 * Settles the invoices ticked on the fee register, in full, as one counter payment.
	 *
	 * <p>The same transaction, the same allocation, the same receipt numbering and the same audit entry
	 * as {@link #record}: this works out the amount and then calls it, rather than being a second way
	 * to take money. One receipt comes out listing every month and fee it covered, which is what the
	 * office hands over when a family clears four months at once.
	 *
	 * <p><strong>The amount is not in the request and cannot be.</strong> It is quoted here from the
	 * invoices and today's late fine, so there is nothing for a client to get wrong or to tamper with.
	 * Paying part of a single month is {@code POST /fees/offline-payment}, which does take an amount.
	 */
	@Transactional
	public Payment markPaid(MarkPaidRequest request) {
		// Midnight of that day in the school's own zone, not UTC's: in India the difference puts an
		// evening payment into the previous day's collection report.
		Instant paidAt = request.paidOn() == null
				? Instant.now(clock)
				: request.paidOn().atStartOfDay(clock.getZone()).toInstant();
		// Quoted as of that date, so "in full" for a backdated entry means the fine that had run by
		// then. record() recomputes the same date from paidAt, so the quote and the allocation agree.
		InvoiceService.CounterQuote quote = invoices.quoteForCounter(request.invoiceIds(), fineDateOf(paidAt));
		return record(new OfflinePaymentRequest(quote.studentUniqueId(), quote.invoiceIds(), quote.amountDue(),
				request.modeOrDefault(), request.reference(), request.payerName(),
				request.payerRelationOrDefault(), paidAt));
	}

	/**
	 * The date a payment's late fine is charged as of: the day the money was received, in the school's
	 * own timezone, and never later than today.
	 *
	 * <p>Clamped because a future {@code paidAt} would quote a fine nobody owes yet and then take it
	 * out of the principal. Money is recorded as it arrives, so a date ahead of today is a typo, and
	 * treating it as today is the reading that cannot overcharge.
	 */
	private LocalDate fineDateOf(Instant paidAt) {
		LocalDate today = LocalDate.now(clock);
		if (paidAt == null) {
			return today;
		}
		LocalDate on = LocalDate.ofInstant(paidAt, clock.getZone());
		return on.isAfter(today) ? today : on;
	}

	// --- recording, for another module ------------------------------------------------------------

	/**
	 * Records a counter payment of a stated amount, taking this module's own types rather than the
	 * controller's request body.
	 *
	 * <p>For a caller in another module that takes money as one step of something larger — B20's
	 * demonstration data seeder, which walks a year of backdated payments in date order so the receipt
	 * numbers come out ascending. It is {@link #record} underneath, with the same transaction, the same
	 * allocation and the same receipt numbering; the annotation is repeated because a self-call would
	 * otherwise miss the proxy and run outside a transaction.
	 *
	 * @param paidOn the day the money was received; null means today. The late fine is charged as of
	 *               this date, not as of today
	 */
	@Transactional
	public Payment recordOffline(String studentUniqueId, List<String> invoiceIds, long amount, PaymentMode mode,
			String reference, String payerName, PayerRelation payerRelation, LocalDate paidOn) {
		return record(new OfflinePaymentRequest(studentUniqueId, invoiceIds, amount, mode, reference, payerName,
				payerRelation, atStartOfDay(paidOn)));
	}

	/**
	 * Settles the named invoices in full — principal plus the late fine as of {@code paidOn} — as one
	 * counter payment, again in this module's own types.
	 *
	 * <p>{@link #markPaid} underneath, so there is still no amount for a caller to get wrong: the
	 * figure is quoted from the invoices. All the ids have to be one student's, because one payment
	 * produces one receipt.
	 */
	@Transactional
	public Payment markPaidInFull(List<String> invoiceIds, PaymentMode mode, String reference, String payerName,
			PayerRelation payerRelation, LocalDate paidOn) {
		return markPaid(new MarkPaidRequest(invoiceIds, mode, reference, paidOn, payerName, payerRelation));
	}

	/** Midnight of that day in the school's own zone, or now when no day was given. */
	private Instant atStartOfDay(LocalDate date) {
		return date == null ? Instant.now(clock) : date.atStartOfDay(clock.getZone()).toInstant();
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
	 * The last ten receipts issued, newest first, reduced to what a dashboard prints (B16).
	 *
	 * <p>CAPTURED only: a gateway order nobody completed is not a taking, and listing one would have
	 * the dashboard announce money that never arrived.
	 *
	 * <p>No caller check — it returns amounts, so the endpoint that calls it is behind
	 * {@code DASHBOARD_ADMIN}, and the projection cannot carry anything finer whatever calls it next.
	 */
	/**
	 * Every captured payment, oldest first — what the accounts backfill (B21) walks to write the
	 * entries the fee collections that predate the module would have produced.
	 *
	 * <p>No caller check, and none is missing: it is not reachable from a request, and the endpoint
	 * behind it is an administrator's. CAPTURED only, for the same reason {@link #recent()} filters —
	 * a gateway order nobody completed is not a taking and must not appear in the books.
	 */
	public List<Payment> capturedPayments() {
		return payments.findByStatusOrderByPaidAtAsc(PaymentStatus.CAPTURED);
	}

	public List<PaymentSummary> recent() {
		return payments.findTop10ByStatusOrderByPaidAtDesc(PaymentStatus.CAPTURED).stream()
				.map(payment -> new PaymentSummary(payment.getId(), payment.getReceiptNo(),
						payment.getStudentUniqueId(),
						students.findRef(payment.getStudentUniqueId()).map(StudentRef::name).orElse(null),
						payment.getAmount(), payment.getMode(), payment.getPaidAt()))
				.toList();
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

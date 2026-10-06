package com.school.payment.app;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.AppException;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.ErrorType;
import com.school.common.exceptions.ForbiddenException;
import com.school.common.exceptions.NotFoundException;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.CurrentUser;
import com.school.fees.app.FeeQuote;
import com.school.fees.app.InvoiceService;
import com.school.payment.api.CreateOrderRequest;
import com.school.payment.api.CreateOrderResponse;
import com.school.payment.api.PaymentStatusResponse;
import com.school.payment.api.VerifyPaymentRequest;
import com.school.payment.domain.Payment;
import com.school.payment.domain.PaymentMode;
import com.school.payment.domain.PaymentStatus;
import com.school.payment.infra.PaymentRepository;
import com.school.people.app.StudentContact;
import com.school.people.app.StudentService;
import com.school.schoolconfig.app.SchoolConfigService;
import org.bson.types.ObjectId;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Paying fees online: creating the order, verifying the handshake, handling the webhook, and
 * reconciling whatever neither of those finished.
 *
 * <p><strong>The amount is always computed here</strong>, from the invoices, and never read from the
 * request (CLAUDE.md rule 4). The client sends invoice ids; the gateway order is created for what
 * those invoices come to.
 *
 * <p><strong>Three ways in, one way through.</strong> The student's verify call, the gateway's webhook
 * and the scheduled reconciliation all end in {@link PaymentSettlement#capture}, which is the same
 * allocate-and-receipt step {@code PaymentService.record} uses for money taken at the counter. The
 * webhook is the authority — it arrives whether or not the family kept the tab open — and the other
 * two are there so nobody waits on it.
 *
 * <p>Nothing in this class logs a key secret, a webhook secret or a signature. What is logged is
 * order ids, payment ids and statuses.
 */
@Service
public class OnlinePaymentService {

	private static final Logger log = LoggerFactory.getLogger(OnlinePaymentService.class);

	/** Everything else from the gateway is ignored, per the task: we act on these two only. */
	private static final String EVENT_CAPTURED = "payment.captured";
	private static final String EVENT_FAILED = "payment.failed";
	private static final Set<String> HANDLED_EVENTS = Set.of(EVENT_CAPTURED, EVENT_FAILED);

	private final PaymentGateway gateway;
	private final PaymentRepository payments;
	private final PaymentService paymentService;
	private final PaymentSettlement settlement;
	private final ReceiptService receipts;
	private final InvoiceService invoices;
	private final StudentService students;
	private final SchoolConfigService schoolConfig;
	private final AuditService audit;
	private final Clock clock;

	public OnlinePaymentService(PaymentGateway gateway, PaymentRepository payments, PaymentService paymentService,
			PaymentSettlement settlement, ReceiptService receipts, InvoiceService invoices, StudentService students,
			SchoolConfigService schoolConfig, AuditService audit, Clock clock) {
		this.gateway = gateway;
		this.payments = payments;
		this.paymentService = paymentService;
		this.settlement = settlement;
		this.receipts = receipts;
		this.invoices = invoices;
		this.students = students;
		this.schoolConfig = schoolConfig;
		this.audit = audit;
		this.clock = clock;
	}

	// --- creating the order -----------------------------------------------------------------------

	/**
	 * Quotes the selected invoices, creates a gateway order for exactly that amount and records the
	 * payment as {@code CREATED}.
	 *
	 * <p>The invoices must be the caller's own and still {@code UNPAID} or {@code PARTIAL}; the fees
	 * module checks both while it works out the figure.
	 *
	 * <p><strong>The payment id is minted before the order, not after.</strong> The gateway order
	 * carries it as the order's {@code receipt}, so an entry in the gateway's dashboard points back at
	 * a row here. Doing it the other way round would mean either inserting a payment that the order
	 * creation might then fail behind — leaving an unpayable {@code CREATED} row — or deleting one,
	 * which payments do not permit. Generating the id locally costs nothing and avoids both.
	 */
	public CreateOrderResponse createOrder(CreateOrderRequest request) {
		AuthPrincipal caller = CurrentUser.require();
		String studentUniqueId = caller.uniqueId();
		if (studentUniqueId == null || students.findRef(studentUniqueId).isEmpty()) {
			// FEE_PAY_SELF only ever belongs to a student, so this is a misconfigured login rather than
			// an ordinary refusal. Still 403 and not 500: there is nothing for this caller to pay.
			throw new ForbiddenException("Only a student may pay their own fees online.");
		}

		FeeQuote quote = invoices.quoteForPayment(studentUniqueId, request.invoiceIds());
		String paymentId = new ObjectId().toHexString();
		String payerName = request.payerName().trim();

		GatewayOrder order = gateway.createOrder(quote.amountDue(), paymentId,
				// Reconciliation aids for whoever is looking at the gateway dashboard, not personal data.
				Map.of("paymentId", paymentId, "studentUniqueId", studentUniqueId,
						"invoiceCount", String.valueOf(quote.invoiceIds().size())));

		Instant now = Instant.now(clock);
		Payment created = payments.insert(Payment.builder()
				.id(paymentId)
				.studentUniqueId(studentUniqueId)
				.mode(PaymentMode.ONLINE)
				.amount(quote.amountDue())
				.invoiceIds(quote.invoiceIds())
				.allocations(List.of())
				.payerName(payerName)
				.payerRelation(request.payerRelation())
				.status(PaymentStatus.CREATED)
				.gatewayOrderId(order.id())
				// recordedBy stays null: nobody at the school recorded this one.
				.createdAt(now)
				.build());
		audit.record(AuditAction.PAYMENT_ORDER_CREATED, PaymentService.AUDIT_ENTITY, created.getId(), null, created);

		StudentContact contact = students.contactFor(studentUniqueId);
		return new CreateOrderResponse(created.getId(), order.id(), order.amount(), PaymentGateway.CURRENCY,
				gateway.keyId(), schoolConfig.identity().name(),
				new CreateOrderResponse.Prefill(payerName, contact.email(), contact.phone()));
	}

	// --- the student's own verification -----------------------------------------------------------

	/**
	 * Verifies the checkout handshake and settles the payment if the gateway agrees that money moved.
	 *
	 * <p>Order of operations matters. The signature is checked first, so an unverified request causes
	 * no gateway traffic at all; then the payment's status is <strong>read back from the gateway</strong>
	 * rather than inferred from the fact that a signed request arrived, because a signature proves the
	 * checkout happened and not that the bank settled. An authorised payment is captured here and then
	 * settled; one already captured is settled straight away.
	 *
	 * <p>A status the gateway has not resolved yet is left alone, and the client keeps polling
	 * {@code GET /payments/{id}}. The webhook or the reconciliation job will finish it.
	 */
	public PaymentStatusResponse verify(VerifyPaymentRequest request) {
		Payment ours = payments.findByGatewayOrderId(request.razorpayOrderId())
				.orElseThrow(() -> NotFoundException.of("Payment order", request.razorpayOrderId()));
		AuthPrincipal caller = CurrentUser.require();
		if (!ours.getStudentUniqueId().equals(caller.uniqueId())) {
			// Object-level, so it cannot live in @PreAuthorize: the endpoint is open to any student,
			// but only to their own order.
			throw new ForbiddenException("You may only verify your own payments.");
		}

		if (!gateway.verifyPaymentSignature(request.razorpayOrderId(), request.razorpayPaymentId(),
				request.razorpaySignature())) {
			// The detail names the check, never what was compared.
			audit.record(AuditAction.PAYMENT_FAILED, PaymentService.AUDIT_ENTITY, ours.getId(),
					"the checkout signature did not verify; nothing was credited");
			log.warn("Rejected a verify call for order {}: the signature did not verify", request.razorpayOrderId());
			throw new ForbiddenException("That payment could not be verified, so nothing has been credited. "
					+ "If the money has left your account it will be credited automatically, or refunded.");
		}

		GatewayPayment attempt = gateway.fetchPayment(request.razorpayPaymentId());
		if (!request.razorpayOrderId().equals(attempt.orderId())) {
			throw new ConflictException("That payment belongs to a different order, so it has not been applied.");
		}
		Payment resolved = apply(ours, attempt);
		return PaymentStatusResponse.of(resolved, paymentService.receiptUrl(resolved));
	}

	// --- the gateway's webhook --------------------------------------------------------------------

	/**
	 * Handles an inbound gateway webhook. The source of truth for whether a payment happened.
	 *
	 * <p>The signature is verified against the <strong>raw body</strong> before anything is parsed,
	 * because this endpoint is unauthenticated and the signature is the only thing standing between it
	 * and the open internet. A body that does not verify is refused with 403.
	 *
	 * <p>Everything else answers 200, and quickly: an event we do not handle, an order that is not
	 * ours, a payment already settled. The gateway retries non-2xx responses, so a 200 for "nothing to
	 * do here" is the difference between a quiet log and a redelivery loop. Genuine failures are
	 * <em>not</em> swallowed — those should be retried, and the reconciliation job is the backstop if
	 * the retries also fail.
	 */
	public void handleWebhook(String payload, String signature) {
		if (!gateway.verifyWebhookSignature(payload, signature)) {
			log.warn("Rejected a payment webhook: the signature did not verify");
			throw new ForbiddenException("The webhook signature did not verify.");
		}

		JSONObject body = parse(payload);
		String event = body.optString("event", "");
		if (!HANDLED_EVENTS.contains(event)) {
			log.debug("Ignoring payment webhook event {}", event);
			return;
		}
		JSONObject entity = paymentEntity(body);
		if (entity == null) {
			log.warn("A {} webhook arrived with no payment entity; ignored", event);
			return;
		}

		String orderId = entity.optString("order_id", null);
		String gatewayPaymentId = entity.optString("id", null);
		Optional<Payment> found = orderId == null ? Optional.empty() : payments.findByGatewayOrderId(orderId);
		if (found.isEmpty()) {
			// Not an error: the same gateway account may serve something else, and a 200 stops the
			// gateway redelivering an event nothing here will ever act on.
			log.info("Ignoring {} for order {}, which is not a payment of ours", event, orderId);
			return;
		}
		Payment ours = found.get();

		if (EVENT_CAPTURED.equals(event)) {
			log.info("Webhook: settling payment {} from gateway payment {}", ours.getId(), gatewayPaymentId);
			settle(ours, gatewayPaymentId, entity.optLong("amount"));
			return;
		}
		String reason = entity.optString("error_description", null);
		log.info("Webhook: marking payment {} failed", ours.getId());
		settlement.fail(ours.getId(), reason == null || reason.isBlank()
				? "The payment did not go through at the gateway."
				: reason);
	}

	// --- reconciliation ---------------------------------------------------------------------------

	/**
	 * Works out what became of one order that was left {@code CREATED}, by asking the gateway what was
	 * ever paid against it.
	 *
	 * <p>Driven by {@link StalePaymentReconciler}. This is the net under the other two paths: a family
	 * that closed the tab before verify ran and a webhook that never arrived both end up here, and
	 * either the money is found and settled or the payment is marked failed so it stops being asked
	 * about.
	 *
	 * <p>An attempt the gateway itself still calls {@code created} is left for the next run rather
	 * than failed — the checkout may be slow, and failing a payment that is about to succeed is how a
	 * capture ends up with nowhere to go.
	 */
	public void reconcile(Payment ours) {
		if (ours.getGatewayOrderId() == null) {
			settlement.fail(ours.getId(), "No gateway order was ever created for this payment.");
			return;
		}

		List<GatewayPayment> attempts = gateway.paymentsForOrder(ours.getGatewayOrderId());
		GatewayPayment settleable = attempts.stream()
				.filter(attempt -> attempt.status() == GatewayPaymentStatus.CAPTURED
						|| attempt.status() == GatewayPaymentStatus.AUTHORIZED)
				.findFirst()
				.orElse(null);
		if (settleable != null) {
			log.info("Reconciling payment {}: gateway payment {} is {}",
					ours.getId(), settleable.id(), settleable.status());
			apply(ours, settleable);
			return;
		}

		boolean stillTrying = attempts.stream()
				.anyMatch(attempt -> attempt.status() == GatewayPaymentStatus.CREATED
						|| attempt.status() == GatewayPaymentStatus.UNKNOWN);
		if (stillTrying) {
			log.debug("Payment {} has an unresolved attempt at the gateway; leaving it for the next run",
					ours.getId());
			return;
		}
		settlement.fail(ours.getId(), attempts.isEmpty()
				? "The checkout was closed without paying."
				: "Every attempt to pay this at the gateway failed.");
	}

	// --- internals --------------------------------------------------------------------------------

	/**
	 * Turns what the gateway says about an attempt into what we do about it. The single decision table,
	 * shared by verify and by reconciliation so the two can never disagree.
	 */
	private Payment apply(Payment ours, GatewayPayment attempt) {
		return switch (attempt.status()) {
			case AUTHORIZED -> {
				// The bank is holding the money; capturing is what actually moves it. The amount has to
				// be what was authorised, which is the order amount this row was created with.
				GatewayPayment captured = gateway.capture(attempt.id(), ours.getAmount());
				yield captured.status() == GatewayPaymentStatus.CAPTURED
						? settle(ours, captured.id(), captured.amount())
						: ours;
			}
			case CAPTURED -> settle(ours, attempt.id(), attempt.amount());
			case FAILED -> settlement.fail(ours.getId(), attempt.errorDescription() == null
					? "The payment did not go through at the gateway."
					: attempt.errorDescription());
			// Captured and then given back. The invoices must not be credited, and the payment is not a
			// payment — but it is also not the family's fault, so the reason says what happened.
			case REFUNDED -> settlement.fail(ours.getId(), "This payment was refunded at the gateway.");
			// Not resolved yet. Left exactly as it is; the webhook or the next reconciliation run decides.
			case CREATED, UNKNOWN -> ours;
		};
	}

	/**
	 * Settles inside a transaction, then renders the receipt outside it.
	 *
	 * <p>The split is the point. The object store is a separate service over the network: writing to
	 * it inside the database transaction would orphan a PDF on every rollback and would make settling a
	 * payment depend on the store being up. So the money and the receipt number commit first, and the
	 * PDF is rendered immediately afterwards — which is what makes a receipt there waiting when the
	 * family's page finishes polling, rather than on their first download.
	 *
	 * <p>A failure to render is logged and swallowed deliberately. The money is in and the payment is
	 * receipted; {@code GET /payments/{id}/receipt.pdf} renders it on demand anyway, so turning a
	 * storage hiccup into a failed settlement would be the worse trade.
	 */
	private Payment settle(Payment ours, String gatewayPaymentId, long capturedAmount) {
		Payment settled = settlement.capture(ours.getId(), gatewayPaymentId, capturedAmount, Instant.now(clock));
		try {
			receipts.ensureRendered(settled);
		}
		catch (RuntimeException ex) {
			log.warn("Payment {} was settled as {} but its receipt PDF could not be rendered yet: {}",
					settled.getId(), settled.getReceiptNo(), ex.getMessage());
		}
		return settled;
	}

	/** {@code payload.payment.entity} of a webhook body, or null if it is not shaped like one. */
	private static JSONObject paymentEntity(JSONObject body) {
		JSONObject wrapper = body.optJSONObject("payload");
		JSONObject payment = wrapper == null ? null : wrapper.optJSONObject("payment");
		return payment == null ? null : payment.optJSONObject("entity");
	}

	/**
	 * Parses a webhook body that has <em>already</em> been signature-verified, so malformed JSON here
	 * means the gateway and this code disagree about the format rather than that somebody sent rubbish.
	 * Still a 400 and not a 500: there is nothing to retry.
	 */
	private static JSONObject parse(String payload) {
		try {
			return new JSONObject(payload);
		}
		catch (JSONException ex) {
			throw new AppException(ErrorType.BAD_REQUEST, "The webhook body was not valid JSON.", ex);
		}
	}
}

package com.school.payment.api;

import java.util.List;

import com.school.common.security.HasPermission;
import com.school.payment.app.OnlinePaymentService;
import com.school.payment.app.PaymentService;
import com.school.payment.app.Receipt;
import com.school.payment.app.ReceiptService;
import com.school.payment.domain.Payment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payments and their receipts, including the online flow a family goes through themselves.
 *
 * <p>{@code @PreAuthorize} only decides who may call at all. <em>Whose</em> payment a caller may read
 * is an object-level rule in {@link PaymentService#requireReadable}: an admin, or the student the
 * payment is for. The same applies to the two online endpoints, which hold the caller to their own
 * invoices and their own order inside {@link OnlinePaymentService}.
 */
@RestController
@RequestMapping("/payments")
@Tag(name = "Payments", description = "Fee payments, receipts and collection reports")
public class PaymentController {

	private final PaymentService payments;
	private final ReceiptService receipts;
	private final OnlinePaymentService online;

	public PaymentController(PaymentService payments, ReceiptService receipts, OnlinePaymentService online) {
		this.payments = payments;
		this.receipts = receipts;
		this.online = online;
	}

	@GetMapping
	@PreAuthorize(HasPermission.FEE_READ_FULL)
	@Operation(summary = "One student's payments, newest first",
			description = "ADMIN only. A student reads their own through /payments/mine.")
	public List<PaymentResponse> forStudent(@RequestParam String studentUniqueId) {
		return payments.forStudent(studentUniqueId).stream()
				.map(payment -> PaymentResponse.of(payment, payments.receiptUrl(payment)))
				.toList();
	}

	@GetMapping("/mine")
	@PreAuthorize(HasPermission.FEE_PAY_SELF)
	@Operation(summary = "Your own payments, newest first",
			description = "For the student, or the parent signed in as them. Empty for a login that is not a "
					+ "student's rather than 404 — having paid nothing is a normal answer.")
	public List<PaymentResponse> mine() {
		return payments.mine().stream()
				.map(payment -> PaymentResponse.of(payment, payments.receiptUrl(payment)))
				.toList();
	}

	@PostMapping("/orders")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.FEE_PAY_SELF)
	@Operation(summary = "Start an online payment for your own invoices",
			description = "STUDENT, or the parent signed in as them. Send invoice ids and who is paying — "
					+ "there is no amount field, because the server computes it from the invoices (outstanding "
					+ "fee plus the late fine due today) and creates the gateway order for exactly that. The "
					+ "invoices must be yours and still UNPAID or PARTIAL. Returns everything the checkout "
					+ "needs, including the publishable keyId; no money has moved yet and the payment is "
					+ "CREATED. 503 if this school has no payment gateway configured.")
	public CreateOrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
		return online.createOrder(request);
	}

	@PostMapping("/verify")
	@PreAuthorize(HasPermission.FEE_PAY_SELF)
	@Operation(summary = "Hand back what the checkout returned",
			description = "STUDENT, for their own order. Verifies the signature, then asks the gateway what "
					+ "actually happened: an authorised payment is captured and settled, a captured one is "
					+ "settled, a failed one is marked FAILED. Settling allocates the money across the "
					+ "invoices and issues the receipt. For speed only — the webhook is the source of truth and "
					+ "settles the same payment through the same code, so closing the tab early loses nothing. "
					+ "Idempotent. 403 if the signature does not verify, and nothing is credited.")
	public PaymentStatusResponse verify(@Valid @RequestBody VerifyPaymentRequest request) {
		return online.verify(request);
	}

	@GetMapping("/{id}")
	@PreAuthorize(HasPermission.AUTHENTICATED)
	@Operation(summary = "Where one payment stands",
			description = "ADMIN, or the student the payment is for; a teacher gets 403. Small on purpose so a "
					+ "client can poll it after a checkout closes: receiptNo arrives with the money.")
	public PaymentStatusResponse status(@PathVariable String id) {
		Payment payment = payments.requireReadable(id);
		return PaymentStatusResponse.of(payment, payments.receiptUrl(payment));
	}

	/**
	 * Served inline so a browser shows the receipt rather than dropping a file in the downloads
	 * folder; the filename is still there for whoever does save it.
	 */
	@GetMapping(value = "/{id}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	@PreAuthorize(HasPermission.AUTHENTICATED)
	@Operation(summary = "The receipt for one payment, as a PDF",
			description = "ADMIN, or the student the payment is for; a teacher gets 403, because a receipt "
					+ "names amounts. Rendered on first request and stored under receipts/; every later request "
					+ "serves the stored copy, so a reprint is byte-for-byte the document already handed over. "
					+ "422 for a payment that was never captured.")
	public ResponseEntity<byte[]> receipt(@PathVariable String id) {
		Receipt receipt = receipts.forPayment(id);
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.inline().filename(receipt.fileName()).build().toString())
				.cacheControl(CacheControl.noStore())
				.body(receipt.pdf());
	}
}

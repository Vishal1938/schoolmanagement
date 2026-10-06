package com.school.payment.api;

import java.time.LocalDate;

import com.school.common.security.HasPermission;
import com.school.payment.app.CollectionReportService;
import com.school.payment.app.PaymentService;
import com.school.payment.domain.Payment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The counter: taking a payment, and reporting what was taken.
 *
 * <p>Mapped under {@code /fees} rather than {@code /payments} because that is where the contract puts
 * it and where an office clerk expects it, even though the work is this module's — the same
 * arrangement as {@code GET /students/{uniqueId}/results} living in exams.
 */
@RestController
@RequestMapping("/fees")
@Tag(name = "Payments", description = "Fee payments, receipts and collection reports")
public class FeeCounterController {

	private final PaymentService payments;
	private final CollectionReportService reports;

	public FeeCounterController(PaymentService payments, CollectionReportService reports) {
		this.payments = payments;
		this.reports = reports;
	}

	@PostMapping("/offline-payment")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Record a payment taken at the counter",
			description = "Allocates the amount across the selected invoices oldest due date first, late fine "
					+ "before fee, in one transaction with the receipt number. The amount is checked against "
					+ "what those invoices actually come to and anything more is refused (422); less is fine and "
					+ "leaves them PARTIAL. A reference is required for every mode but CASH. The receipt PDF is "
					+ "rendered on first download, not here.")
	public PaymentResponse offlinePayment(@Valid @RequestBody OfflinePaymentRequest request) {
		Payment payment = payments.record(request);
		return PaymentResponse.of(payment, payments.receiptUrl(payment));
	}

	@GetMapping("/reports/collection")
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "What came in over a date range",
			description = "Both dates inclusive, resolved in the school's own timezone. Defaults to the "
					+ "current month to date. Totals by mode, by head and by day; only CAPTURED payments count. "
					+ "byHead sums to principalCollected, because a late fine belongs to no head.")
	public CollectionReportResponse collection(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return reports.collection(from, to);
	}
}

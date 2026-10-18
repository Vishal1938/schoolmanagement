package com.school.payment.api;

import java.time.LocalDate;

import com.school.common.security.HasPermission;
import com.school.fees.domain.InstallmentKind;
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

	@PostMapping("/register/mark-paid")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Settle the ticked cells of the fee register in full",
			description = "One counter payment covering those invoices completely, with one receipt listing "
					+ "every month and fee it paid. There is no amount in the request: the server quotes the "
					+ "invoices itself, outstanding principal plus whatever late fine is running today, and "
					+ "takes exactly that. All the invoices must be one student's → 400 otherwise, because one "
					+ "payment means one receipt. mode defaults to CASH and needs a reference for anything else; "
					+ "paidOn defaults to today. For part payment of a single invoice, use "
					+ "POST /fees/offline-payment, which does take an amount.")
	public PaymentResponse markPaid(@Valid @RequestBody MarkPaidRequest request) {
		Payment payment = payments.markPaid(request);
		return PaymentResponse.of(payment, payments.receiptUrl(payment));
	}

	@GetMapping("/reports/collection")
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "What came in over a date range",
			description = "Both dates inclusive, resolved in the school's own timezone. Defaults to the "
					+ "current month to date. Totals by mode, by head and by day; only CAPTURED payments count. "
					+ "byHead sums to principalCollected, because a late fine belongs to no head. Pass kind to "
					+ "report one sort of charge only — MONTHLY for the recurring fees, ONE_TIME for admission "
					+ "fees and deposits, CUSTOM for individual charges and fines — in which case every figure "
					+ "counts only the allocations of that kind and payments touching none of it are left out.")
	public CollectionReportResponse collection(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) InstallmentKind kind) {
		return reports.collection(from, to, kind);
	}
}

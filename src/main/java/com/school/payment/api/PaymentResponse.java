package com.school.payment.api;

import java.time.Instant;
import java.util.List;

import com.school.payment.domain.Payment;
import com.school.payment.domain.PaymentAllocation;
import com.school.payment.domain.PayerRelation;
import com.school.payment.domain.PaymentMode;
import com.school.payment.domain.PaymentStatus;

/**
 * One payment. All amounts are paise.
 *
 * <p>The last four fields are the online flow's and are simply absent for money taken at the counter,
 * which is most of them.
 *
 * @param principalAmount    the part that settled what was billed
 * @param lateFineAmount     the part that settled late fines
 * @param receiptUrl         this API's own download path for the receipt PDF, or null before capture
 * @param unallocatedAmount  money taken that no invoice needed and that is owed back. Absent — not
 *                           zero — for every payment that does not have this problem
 * @param failureReason      why a gateway payment did not go through. Absent unless FAILED
 */
public record PaymentResponse(
		String id,
		String studentUniqueId,
		PaymentMode mode,
		long amount,
		long principalAmount,
		long lateFineAmount,
		List<PaymentAllocation> allocations,
		String reference,
		String payerName,
		PayerRelation payerRelation,
		String receiptNo,
		String receiptUrl,
		PaymentStatus status,
		String recordedBy,
		Instant paidAt,
		String gatewayOrderId,
		String gatewayPaymentId,
		Long unallocatedAmount,
		String failureReason) {

	public static PaymentResponse of(Payment payment, String receiptUrl) {
		return new PaymentResponse(
				payment.getId(),
				payment.getStudentUniqueId(),
				payment.getMode(),
				payment.getAmount(),
				payment.principalTotal(),
				payment.lateFineTotal(),
				payment.getAllocations() == null ? List.<PaymentAllocation>of() : payment.getAllocations(),
				payment.getReference(),
				payment.getPayerName(),
				payment.getPayerRelation(),
				payment.getReceiptNo(),
				receiptUrl,
				payment.getStatus(),
				payment.getRecordedBy(),
				payment.getPaidAt(),
				payment.getGatewayOrderId(),
				payment.getGatewayPaymentId(),
				payment.getUnallocatedAmount(),
				payment.getFailureReason());
	}
}

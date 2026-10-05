package com.school.payment.infra;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.school.payment.domain.Payment;
import com.school.payment.domain.PaymentMode;
import com.school.payment.domain.PaymentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Repository for {@code payments}. Internal to this module; callers use the services in {@code app}. */
public interface PaymentRepository extends MongoRepository<Payment, String> {

	/** A student's payment history, newest first. Backs both {@code /payments} and {@code /payments/mine}. */
	List<Payment> findByStudentUniqueIdOrderByPaidAtDesc(String studentUniqueId);

	/**
	 * Captured payments in a half-open instant range, for the collection report. Only CAPTURED rows
	 * are money: a created-but-unpaid gateway order would otherwise inflate every total.
	 */
	List<Payment> findByStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtAsc(
			PaymentStatus status, Instant from, Instant to);

	/**
	 * Our payment for a gateway order. The order id is all the verify call and the webhook carry, so
	 * this is how both find the row they are about to settle.
	 */
	Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

	/**
	 * Gateway orders left unresolved past {@code cutoff}, oldest first, for the reconciliation job.
	 * Capped because a backlog is a signal to look rather than something to grind through in one pass.
	 */
	List<Payment> findTop100ByStatusAndModeAndCreatedAtLessThanOrderByCreatedAtAsc(
			PaymentStatus status, PaymentMode mode, Instant cutoff);
}

package com.school.payment.infra;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.school.payment.domain.Payment;
import com.school.payment.domain.PaymentMode;
import com.school.payment.domain.PaymentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

/** Repository for {@code payments}. Internal to this module; callers use the services in {@code app}. */
public interface PaymentRepository extends MongoRepository<Payment, String> {

	/** A student's payment history, newest first. Backs both {@code /payments} and {@code /payments/mine}. */
	List<Payment> findByStudentUniqueIdOrderByPaidAtDesc(String studentUniqueId);

	/**
	 * Captured payments in a half-open instant range, for the collection report. Only CAPTURED rows
	 * are money: a created-but-unpaid gateway order would otherwise inflate every total.
	 *
	 * <p>Written out as a {@code @Query} rather than derived from the method name, and that is
	 * load-bearing. A derived name mentioning one property in two range clauses — the
	 * {@code ...PaidAtGreaterThanEqualAndPaidAtLessThan...} this replaces — becomes
	 * {@code and("paidAt")} twice over, and a single {@code org.bson.Document} cannot hold the key
	 * twice: {@code InvalidMongoDbApiUsageException}, thrown while building the query, so it failed
	 * every call and not merely some data. {@code Between} would derive cleanly but is inclusive at
	 * both ends, and {@code to} here is the next day's midnight, which would count a payment made
	 * exactly then in two days at once.
	 *
	 * <p>Served by {@code payments_status_paid_at_idx}.
	 */
	@Query(value = "{ 'status': ?0, 'paidAt': { '$gte': ?1, '$lt': ?2 } }", sort = "{ 'paidAt': 1 }")
	List<Payment> findCapturedInRange(PaymentStatus status, Instant from, Instant to);

	/**
	 * Payments raised against any of these invoices, for the demonstration data reset (B20): a payment
	 * carries no session, so "this session's payments" is asked of the invoices it names.
	 */
	List<Payment> findByInvoiceIdsIn(Collection<String> invoiceIds);

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

	/**
	 * The last few receipts issued, newest first, for the admin dashboard. Served by the
	 * {@code status, paidAt} index the collection report already needs.
	 */
	List<Payment> findTop10ByStatusOrderByPaidAtDesc(PaymentStatus status);
}

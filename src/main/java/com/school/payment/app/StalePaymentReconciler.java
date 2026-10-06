package com.school.payment.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.school.payment.domain.Payment;
import com.school.payment.domain.PaymentMode;
import com.school.payment.domain.PaymentStatus;
import com.school.payment.infra.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Chases up gateway orders that nothing finished.
 *
 * <p>Both of the fast paths can be missed. A family can close the tab before the verify call runs,
 * and a webhook can be lost, delayed or rejected while this service is restarting. Either leaves a
 * payment sitting at {@code CREATED} while the family's money has actually left their account — so
 * the ledger is asked, every quarter of an hour, what it is still waiting for, and the gateway is
 * asked what became of it.
 *
 * <p>Fifteen minutes is also the staleness threshold, not just the interval: a checkout a minute old
 * is simply in progress, and asking the gateway about it would be noise.
 */
@Component
public class StalePaymentReconciler {

	private static final Logger log = LoggerFactory.getLogger(StalePaymentReconciler.class);

	/** How long an order may sit at {@code CREATED} before it is somebody's problem. */
	private static final Duration STALE_AFTER = Duration.ofMinutes(15);

	private final OnlinePaymentService online;
	private final PaymentGateway gateway;
	private final PaymentRepository payments;
	private final Clock clock;

	public StalePaymentReconciler(OnlinePaymentService online, PaymentGateway gateway, PaymentRepository payments,
			Clock clock) {
		this.online = online;
		this.gateway = gateway;
		this.payments = payments;
		this.clock = clock;
	}

	/**
	 * Every fifteen minutes, resolves the online payments left {@code CREATED} for longer than that.
	 *
	 * <p>{@code fixedDelay}, not {@code fixedRate}: the gap is measured from the end of the previous
	 * run, so a slow pass over a backlog does not have the next one starting on top of it. The initial
	 * delay keeps it out of the way of startup.
	 *
	 * <p>One payment's failure does not stop the pass. Each is reconciled in its own transaction and a
	 * failure is logged and stepped over — the whole point of this job is to be the thing that still
	 * runs when something else has gone wrong, and the next run will try again.
	 */
	@Scheduled(initialDelay = 2, fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
	public void reconcileStaleOrders() {
		if (!gateway.isConfigured()) {
			return;
		}
		Instant cutoff = Instant.now(clock).minus(STALE_AFTER);
		List<Payment> stale = payments.findTop100ByStatusAndModeAndCreatedAtLessThanOrderByCreatedAtAsc(
				PaymentStatus.CREATED, PaymentMode.ONLINE, cutoff);
		if (stale.isEmpty()) {
			return;
		}

		log.info("Reconciling {} online payment(s) left unresolved for over {} minutes",
				stale.size(), STALE_AFTER.toMinutes());
		int resolved = 0;
		for (Payment payment : stale) {
			try {
				online.reconcile(payment);
				resolved++;
			}
			catch (RuntimeException ex) {
				log.warn("Could not reconcile payment {}: {}", payment.getId(), ex.getMessage());
			}
		}
		log.info("Reconciled {} of {} unresolved online payment(s)", resolved, stale.size());
	}
}

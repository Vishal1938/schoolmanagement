package com.school.payment.app;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.school.common.id.IdGenerator;
import com.school.payment.domain.Payment;
import com.school.payment.infra.PaymentRepository;
import com.school.schoolconfig.app.SchoolConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Wipes the payments raised against a set of invoices, their receipt PDFs, and the receipt counter.
 *
 * <p>Counterpart of {@code FeeDataResetService} on the money side, and here for the same reason: the
 * demonstration data reset (B20) must not reach into this module's repository or know that a receipt
 * is stored under {@code receipts/{receiptNo}.pdf}.
 *
 * <p><strong>A payment carries no session</strong>, deliberately — it is money against named invoices
 * and nothing else — so "this session's payments" is a question about the invoices. The caller hands
 * the invoice ids over, which is also the order the wipe has to happen in: the payments go before the
 * invoices they point at.
 *
 * <p>Payments are otherwise <em>append-only</em> in this module. This is the single exception, it is
 * only reachable with the demonstration data feature switched on, and it deletes the receipt files
 * alongside the rows — a receipt PDF left in the bucket with no payment behind it would still be
 * downloadable by its key and would still say a family had paid.
 */
@Service
public class PaymentDataResetService {

	private static final Logger log = LoggerFactory.getLogger(PaymentDataResetService.class);

	private final PaymentRepository payments;
	private final ReceiptService receipts;
	private final SchoolConfigService schoolConfig;
	private final IdGenerator idGenerator;
	private final Clock clock;

	public PaymentDataResetService(PaymentRepository payments, ReceiptService receipts,
			SchoolConfigService schoolConfig, IdGenerator idGenerator, Clock clock) {
		this.payments = payments;
		this.receipts = receipts;
		this.schoolConfig = schoolConfig;
		this.idGenerator = idGenerator;
		this.clock = clock;
	}

	/**
	 * Whether any money has been taken against any of these invoices.
	 *
	 * <p>The guard that stops the demonstration seeder running on top of real takings: once a receipt
	 * has been handed to a family, re-seeding would double the collection reports rather than refresh
	 * them.
	 */
	public boolean anyPaymentFor(Collection<String> invoiceIds) {
		return invoiceIds != null && !invoiceIds.isEmpty() && !payments.findByInvoiceIdsIn(invoiceIds).isEmpty();
	}

	/**
	 * Removes those payments, the receipt PDFs they produced, and the receipt counter.
	 *
	 * <p>Not transactional, and the order matters: the file goes first and the row second, because a
	 * row with no file re-renders harmlessly on the next download while a file with no row is a receipt
	 * nobody can account for. Re-running it finishes an interrupted wipe.
	 *
	 * <p>The counter is reset last, so the next demonstration run numbers its receipts from 1 again.
	 * That is only safe because every receipt issued under it has just been deleted — see
	 * {@link IdGenerator#resetSequence}.
	 */
	public PaymentDataPurge purgeForInvoices(Collection<String> invoiceIds) {
		List<Payment> taken = invoiceIds == null || invoiceIds.isEmpty()
				? List.of()
				: payments.findByInvoiceIdsIn(invoiceIds);

		int receiptsDeleted = 0;
		for (Payment payment : taken) {
			if (payment.getReceiptNo() != null) {
				receipts.deleteStored(payment.getReceiptNo());
				receiptsDeleted++;
			}
		}
		payments.deleteAll(taken);
		boolean counterReset = resetReceiptCounter();

		PaymentDataPurge purge = new PaymentDataPurge(taken.size(), receiptsDeleted, counterReset);
		log.info("Wiped the payments of {} invoices: {}", invoiceIds == null ? 0 : invoiceIds.size(), purge);
		return purge;
	}

	/**
	 * Puts the receipt sequence for the current year back to zero.
	 *
	 * @return false when this deployment has no receipt prefix configured, in which case no receipt was
	 *         ever numbered and there is nothing to reset
	 */
	private boolean resetReceiptCounter() {
		String prefix = schoolConfig.receiptPrefix();
		if (prefix == null || prefix.isBlank()) {
			return false;
		}
		// The same normalisation nextNumber() applies, so this is the key the receipts were drawn from.
		idGenerator.resetSequence(IdGenerator.counterKey(prefix.trim().toUpperCase(Locale.ROOT),
				LocalDate.now(clock).getYear()));
		return true;
	}

	/** What a wipe removed. */
	public record PaymentDataPurge(int payments, int receiptFiles, boolean receiptCounterReset) {

		/** For the response and the audit entry. */
		public Map<String, Object> asCounts() {
			return Map.of("payments", payments, "receiptFiles", receiptFiles,
					"receiptCounterReset", receiptCounterReset);
		}
	}
}

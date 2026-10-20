package com.school.demodata.app;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.school.payment.domain.PayerRelation;
import com.school.payment.domain.PaymentMode;

/**
 * One payment the seeder intends to take, before it takes it.
 *
 * <p><strong>Planned first and executed afterwards, all of them together, in date order.</strong>
 * That is the whole reason this type exists. Receipt numbers come off a single counter in the order
 * payments are recorded, so walking the courses student by student would hand receipt 1 to a payment
 * made in October and receipt 40 to one made in July — and a receipt book whose numbers do not follow
 * its dates is the first thing an auditor notices.
 *
 * @param amount null means "settle these invoices in full, whatever the late fine as of {@code paidOn}
 *               comes to", which is {@code markPaidInFull}. A figure means a part payment, which is
 *               {@code recordOffline}
 * @param what   a short description used only in a warning if this payment is refused
 */
record PlannedPayment(LocalDate paidOn, String studentUniqueId, List<String> invoiceIds, Long amount,
		PaymentMode mode, String reference, String payerName, PayerRelation payerRelation, String what) {

	/**
	 * Date first, then the student, then the description.
	 *
	 * <p>The tie-breakers matter: two payments on the same day have to be ordered by something stable
	 * or the receipt numbers would come out differently on each run, and "deterministic" is what the
	 * specification asks of this data.
	 */
	static final Comparator<PlannedPayment> IN_DATE_ORDER = Comparator
			.comparing(PlannedPayment::paidOn)
			.thenComparing(PlannedPayment::studentUniqueId)
			.thenComparing(PlannedPayment::what);

	/** In full, with the fine computed as of the day the money arrived. */
	static PlannedPayment inFull(LocalDate paidOn, String studentUniqueId, List<String> invoiceIds,
			PaymentMode mode, String reference, String payerName, PayerRelation payerRelation, String what) {
		return new PlannedPayment(paidOn, studentUniqueId, invoiceIds, null, mode, reference, payerName,
				payerRelation, what);
	}

	/** A stated amount, which leaves the invoice PARTIAL. */
	static PlannedPayment ofAmount(LocalDate paidOn, String studentUniqueId, List<String> invoiceIds, long amount,
			PaymentMode mode, String reference, String payerName, PayerRelation payerRelation, String what) {
		return new PlannedPayment(paidOn, studentUniqueId, invoiceIds, amount, mode, reference, payerName,
				payerRelation, what);
	}

	boolean settlesInFull() {
		return amount == null;
	}
}

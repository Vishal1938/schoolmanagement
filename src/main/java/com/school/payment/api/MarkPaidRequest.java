package com.school.payment.api;

import java.time.LocalDate;
import java.util.List;

import com.school.payment.domain.PayerRelation;
import com.school.payment.domain.PaymentMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /fees/register/mark-paid}: settle the ticked cells of the register in full.
 *
 * <p><strong>There is no amount.</strong> That is the point of this endpoint as against
 * {@code POST /fees/offline-payment}: the clerk ticks "July, August and the admission fee", and the
 * server works out what that comes to — outstanding principal plus whatever late fine is running
 * today — and takes exactly that (CLAUDE.md rule 4, in its strongest form: the client cannot even
 * express a figure). A family paying something less than the full amount of one month is the other
 * endpoint, which does take an amount and leaves the invoice {@code PARTIAL}.
 *
 * <p>Every invoice must be the same student's. One payment has one payer and produces one receipt,
 * and that receipt lists every month and fee it covered.
 *
 * @param invoiceIds    what is being paid. All one student's, all still payable
 * @param mode          defaults to {@code CASH}, which is what a counter mostly takes. {@code ONLINE}
 *                      is rejected — that is the gateway's flow
 * @param reference     the cheque number, UTR or UPI reference. Required for every mode but CASH
 * @param paidOn        the date the money was received, resolved in the school's own timezone;
 *                      defaults to today. A cheque banked on Monday and entered on Tuesday belongs to
 *                      Monday's collection
 * @param payerName     who handed it over, as they gave their name. Printed on the receipt — there are
 *                      no parent accounts in this system, so this is the only record of who paid
 * @param payerRelation defaults to {@code OTHER} when the clerk did not ask
 */
public record MarkPaidRequest(
		@NotEmpty @Size(max = 40) List<@NotBlank String> invoiceIds,
		PaymentMode mode,
		@Size(max = 60) String reference,
		LocalDate paidOn,
		@NotBlank @Size(max = 120) String payerName,
		PayerRelation payerRelation) {

	/** Cash unless the clerk said otherwise. */
	public PaymentMode modeOrDefault() {
		return mode == null ? PaymentMode.CASH : mode;
	}

	/** OTHER unless the clerk said otherwise; the name is what the receipt really needs. */
	public PayerRelation payerRelationOrDefault() {
		return payerRelation == null ? PayerRelation.OTHER : payerRelation;
	}
}

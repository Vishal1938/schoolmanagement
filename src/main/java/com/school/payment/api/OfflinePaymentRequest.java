package com.school.payment.api;

import java.time.Instant;
import java.util.List;

import com.school.payment.domain.PayerRelation;
import com.school.payment.domain.PaymentMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /fees/offline-payment}: money taken at the counter.
 *
 * <p>{@code amount} is what the clerk actually received, in paise. It is <strong>checked against the
 * invoices, never trusted</strong> (CLAUDE.md rule 4): the server works out what the selected
 * invoices come to, including the late fine, and refuses anything more than that. A short amount is
 * fine and leaves invoices {@code PARTIAL}.
 *
 * @param mode     {@code ONLINE} is rejected here — that is the gateway's flow (B12), not a counter
 *                 receipt
 * @param paidAt   when the money was received; defaults to now. A cheque banked on Monday and entered
 *                 on Tuesday should be reported under Monday
 * @param payerName who handed it over, as they gave their name. Printed on the receipt
 */
public record OfflinePaymentRequest(
		@NotBlank String studentUniqueId,
		@NotEmpty @Size(max = 40) List<@NotBlank String> invoiceIds,
		@Positive long amount,
		@NotNull PaymentMode mode,
		@Size(max = 60) String reference,
		@NotBlank @Size(max = 120) String payerName,
		@NotNull PayerRelation payerRelation,
		Instant paidAt) {
}

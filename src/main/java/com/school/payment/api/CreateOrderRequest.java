package com.school.payment.api;

import java.util.List;

import com.school.payment.domain.PayerRelation;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /payments/orders}: which invoices the family wants to pay online.
 *
 * <p><strong>There is deliberately no amount field.</strong> The server works out what the selected
 * invoices come to — outstanding principal plus the live late fine — and creates the gateway order
 * for exactly that (CLAUDE.md rule 4). A client that could name its own figure could pay a rupee for
 * a term's fees.
 *
 * <p>Nor is there a student field: the invoices must belong to the caller, and the caller is the
 * student (or the parent signed in as them).
 *
 * @param payerName     who is actually paying, as they give their name — a parent, usually. Printed
 *                      on the receipt, because this system has no parent accounts for it to infer
 * @param payerRelation their relation to the student, printed beside the name
 */
public record CreateOrderRequest(
		@NotEmpty @Size(max = 40) List<@NotBlank String> invoiceIds,
		@NotBlank @Size(max = 120) String payerName,
		@NotNull PayerRelation payerRelation) {
}

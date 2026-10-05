package com.school.fees.app;

import java.util.List;

/**
 * What a set of invoices comes to right now, as the fees module quotes it to whoever is about to take
 * money for them.
 *
 * <p>This is the answer to CLAUDE.md rule 4 for the online flow: the client sends invoice ids and
 * never an amount, and {@link #amountDue} is computed here from the stored principal and the live
 * late fine. It is a quote and not a promise — the fine grows by the day, and the figure is computed
 * again before the money is allocated.
 *
 * @param invoiceIds the invoices actually covered, which may be fewer than were asked for when this
 *                   is a re-quote of an order being settled
 * @param amountDue  outstanding principal plus late fine across those invoices, in paise
 */
public record FeeQuote(List<String> invoiceIds, long amountDue) {
}

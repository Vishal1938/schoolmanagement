package com.school.payment.app;

/**
 * An order created at the gateway: the intent to collect a fixed amount, which the checkout is then
 * opened against.
 *
 * @param id       the gateway's order id, e.g. {@code order_PqR...}. Stored on the payment, and the
 *                 only thing a webhook gives us to find our own row by
 * @param amount   what the order is for, in paise, as the gateway echoed it back
 * @param currency always {@code INR} for this deployment
 * @param receipt  our own payment id, passed through so the gateway dashboard points back at us
 */
public record GatewayOrder(String id, long amount, String currency, String receipt) {
}

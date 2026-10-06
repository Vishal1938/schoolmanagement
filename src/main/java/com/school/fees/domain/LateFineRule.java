package com.school.fees.domain;

/**
 * What a school charges for paying late. Part of the structure, not of the invoice: the fine is
 * recomputed from this rule every time an invoice is read, so correcting the grace days corrects
 * every invoice at once rather than only the ones issued afterwards.
 *
 * @param type      FLAT once, or PER_DAY for every day past the grace period
 * @param amount    paise — the whole fine for FLAT, the daily rate for PER_DAY
 * @param graceDays days after the due date before anything is charged; 0 charges from the next day
 * @param cap       the most that can ever be charged on one invoice, in paise. {@code 0} means no cap,
 *                  which only matters for PER_DAY — an uncapped daily fine on a forgotten invoice
 *                  grows without limit
 */
public record LateFineRule(LateFineType type, long amount, int graceDays, long cap) {
}

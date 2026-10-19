package com.school.payroll.app;

/**
 * What one month's payroll still owes, as other modules see it.
 *
 * <p>Zero both ways means one of two very different things — the month has been paid in full, or
 * nobody has run it yet — which is why {@code month} travels with the figures: a dashboard showing
 * "₹0 pending" for a month with no run would be reassuring and wrong.
 *
 * @param month  {@code yyyy-MM}
 * @param count  PENDING salary records
 * @param amount their net, in paise: wages computed but not yet paid out
 */
public record PendingSalaries(String month, int count, long amount) {
}

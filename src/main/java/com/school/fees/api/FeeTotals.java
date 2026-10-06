package com.school.fees.api;

/**
 * A student's fee position across every invoice that is not cancelled. All amounts are paise.
 *
 * @param totalNet     sum of {@code netAmount} — what has been billed after concessions
 * @param totalPaid    principal collected, excluding late fines taken
 * @param lateFineDue  fines running right now, computed on read
 * @param balance      {@code totalNet - totalPaid + lateFineDue}: what the family owes today
 */
public record FeeTotals(long totalNet, long totalPaid, long lateFineDue, long balance) {
}

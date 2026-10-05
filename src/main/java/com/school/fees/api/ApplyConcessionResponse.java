package com.school.fees.api;

/**
 * Result of {@code POST /fees/concessions/{id}/apply}.
 *
 * @param recalculated invoices whose concession and {@code netAmount} were rewritten
 * @param skipped      UNPAID invoices whose concession already came to the same figure, so nothing was
 *                     written. PARTIAL, PAID and CANCELLED invoices are not considered at all and are
 *                     not counted here — a bill already part-settled is not restated under a family
 */
public record ApplyConcessionResponse(int recalculated, int skipped) {
}

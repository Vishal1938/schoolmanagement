package com.school.fees.app;

/**
 * What one {@code generate-invoices} run did, for a caller in another module.
 *
 * <p>The same two numbers the endpoint answers with, in this module's own package: generation is
 * idempotent, so {@code created: 0, skipped: 160} means "nothing left to do" rather than a failure.
 */
public record InvoiceCounts(int created, int skipped) {
}

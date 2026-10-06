package com.school.fees.api;

/**
 * Result of {@code POST /fees/structures/{id}/generate-invoices}.
 *
 * @param created invoices written by this call
 * @param skipped rows that already existed, which is the normal answer to running it twice — the call
 *                is idempotent, so {@code {created: 0, skipped: 160}} means "nothing left to do"
 */
public record GenerateInvoicesResponse(int created, int skipped) {
}

package com.school.fees.api;

import java.util.List;

import com.school.common.fees.FeeStatus;

/**
 * {@code GET /fees/students/{uniqueId}}: the whole ledger plus the totals.
 *
 * <p>For an admin, or for the student themselves — never for a teacher, who gets
 * {@code /status} and no amounts at all (CLAUDE.md rule 2).
 */
public record StudentFeesResponse(
		String studentUniqueId,
		String name,
		String classId,
		String className,
		String section,
		FeeStatus feeStatus,
		List<InvoiceResponse> invoices,
		FeeTotals totals) {
}

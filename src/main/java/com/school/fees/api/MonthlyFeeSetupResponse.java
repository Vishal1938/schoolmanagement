package com.school.fees.api;

/**
 * What {@code POST /fees/structures/monthly} did, and the structure as it now stands.
 *
 * <p>The three counts are what makes re-running it safe to do: an admin who corrects the figure and
 * runs it again can see at a glance how many months took the new amount and how many were left alone
 * because a family has already been billed for them.
 *
 * @param monthsCreated months that were not on the structure and now are
 * @param monthsUpdated months that were already there, had no invoices, and took the new amount
 * @param monthsSkipped months left untouched because invoices have already been raised from them. The
 *                      way to change one of those is a concession or a credit, not an edit
 */
public record MonthlyFeeSetupResponse(
		int monthsCreated,
		int monthsUpdated,
		int monthsSkipped,
		FeeStructureResponse structure) {
}

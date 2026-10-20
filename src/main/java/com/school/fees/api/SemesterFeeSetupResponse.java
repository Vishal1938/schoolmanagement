package com.school.fees.api;

/**
 * What {@code POST /fees/structures/semester} did, and the structure as it now stands.
 *
 * <p>The same three counts as {@link MonthlyFeeSetupResponse}, for the same reason: re-running the
 * call to correct a figure has to show which semesters took the new amount and which were left alone
 * because a family has already been billed for them.
 *
 * @param semestersCreated semesters that were not on the structure and now are
 * @param semestersUpdated semesters that were already there, had no invoices, and took the new amount
 * @param semestersSkipped semesters left untouched because invoices have already been raised from
 *                         them. The way to change one of those is a concession or a credit, not an edit
 */
public record SemesterFeeSetupResponse(
		int semestersCreated,
		int semestersUpdated,
		int semestersSkipped,
		FeeStructureResponse structure) {
}

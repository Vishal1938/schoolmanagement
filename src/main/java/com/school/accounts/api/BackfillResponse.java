package com.school.accounts.api;

/**
 * What {@code POST /accounts/backfill} did.
 *
 * <p>{@code skipped} is the normal answer on a second run and is not a failure: every automatic
 * entry is keyed on what it came from, so a record already booked is left exactly as it was —
 * including any money account somebody has since corrected by hand.
 */
public record BackfillResponse(
		int feesWritten,
		int feesSkipped,
		int salariesWritten,
		int salariesSkipped,
		int advancesWritten,
		int advancesSkipped,
		int recoveriesWritten,
		int recoveriesSkipped) {

	/** Total entries this run added, across all four kinds. */
	public int totalWritten() {
		return feesWritten + salariesWritten + advancesWritten + recoveriesWritten;
	}
}

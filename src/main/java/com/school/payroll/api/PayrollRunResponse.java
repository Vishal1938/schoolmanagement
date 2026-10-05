package com.school.payroll.api;

import java.util.List;

/**
 * What one {@code POST /payroll/runs} did.
 *
 * <p>Running the same month again is safe and is the normal way to finish a run after fixing a
 * structure: everybody already done comes back under {@code skipped}, and only the gaps are filled.
 *
 * @param created          records written by this run
 * @param skipped          employees who already had a record for the month, left untouched
 * @param missingStructure active employees with no salary structure effective for the month, as
 *                         {@code "Name (UNIQUE-ID)"} so the office can go and fix them. Nothing was
 *                         written for these, and a rerun picks them up
 */
public record PayrollRunResponse(
		String month,
		boolean prorateByAttendance,
		int created,
		int skipped,
		List<String> missingStructure) {
}

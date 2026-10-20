package com.school.payroll.app;

import java.util.List;

/**
 * What one payroll run did, for a caller in another module.
 *
 * <p>The same four figures the endpoint answers with, in this module's own package.
 *
 * @param month            {@code yyyy-MM}
 * @param skipped          employees who already had a record for the month. A run is idempotent, so
 *                         this is the normal answer to running it twice
 * @param missingStructure employees who were not paid because nothing is agreed for them yet, named
 *                         so the office can fix it and run the month again
 */
public record PayrollRunResult(String month, boolean prorateByAttendance, int created, int skipped,
		List<String> missingStructure) {
}

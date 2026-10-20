package com.school.demodata.api;

import java.util.Map;
import java.util.TreeMap;

/**
 * What a wipe deleted, per collection.
 *
 * <p>A map rather than a field per collection, because the collections involved are the business of
 * the four modules that own them and this endpoint only adds the counts up. The keys are
 * {@code payments}, {@code receiptFiles}, {@code receiptCounterReset}, {@code feeStructures},
 * {@code invoices}, {@code concessions}, {@code payrollRecords}, {@code salaryStructures},
 * {@code salaryAdvances}, {@code employeeAttendanceDays} and {@code holidays}.
 *
 * @param deleted counts, in key order so two runs read the same way
 */
public record DemoDataResetResponse(Map<String, Object> deleted) {

	public static DemoDataResetResponse of(Map<String, Object> deleted) {
		return new DemoDataResetResponse(new TreeMap<>(deleted));
	}
}

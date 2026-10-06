package com.school.payroll.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code POST /payroll/runs}: compute one month's salaries.
 *
 * @param month                the month to pay for, as {@code yyyy-MM}. Not in the future — there is
 *                             no attendance for a month that has not happened
 * @param prorateByAttendance  when true, pay is docked for days marked ABSENT and HALF_DAY in the
 *                             employee register. LEAVE, holidays, non-working days and days nobody
 *                             marked are paid either way
 */
public record PayrollRunRequest(
		@NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "must be a month as yyyy-MM")
		String month,
		boolean prorateByAttendance) {
}

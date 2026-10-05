package com.school.attendance.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

import com.school.attendance.domain.AttendanceStatus;

/**
 * The totals over a date range, for a student or an employee.
 *
 * <p>{@code workingDays} is <strong>the number of days that were actually marked, excluding
 * LEAVE</strong> — the denominator of {@code percentage} — not the number of working days in the
 * calendar range. A range with twenty working days of which twelve were marked, one of them as
 * leave, gives {@code workingDays: 11}. Unmarked days do not count against anybody: a register
 * nobody submitted says nothing about whether a student was there.
 *
 * <p>{@code percentage = (present + late + 0.5 × halfDay) / workingDays × 100}, to two decimals, and
 * {@code 0} when nothing counted.
 */
public record AttendanceSummary(
		int present,
		int absent,
		int late,
		int halfDay,
		int leave,
		int workingDays,
		double percentage) {

	public static AttendanceSummary of(Collection<AttendanceStatus> statuses) {
		int present = 0;
		int absent = 0;
		int late = 0;
		int halfDay = 0;
		int leave = 0;
		int counted = 0;
		double weighted = 0;

		for (AttendanceStatus status : statuses) {
			switch (status) {
				case PRESENT -> present++;
				case ABSENT -> absent++;
				case LATE -> late++;
				case HALF_DAY -> halfDay++;
				case LEAVE -> leave++;
			}
			if (status.countsTowardsPercentage()) {
				counted++;
				weighted += status.presenceWeight();
			}
		}

		double percentage = counted == 0 ? 0
				: BigDecimal.valueOf(weighted * 100 / counted).setScale(2, RoundingMode.HALF_UP).doubleValue();
		return new AttendanceSummary(present, absent, late, halfDay, leave, counted, percentage);
	}
}

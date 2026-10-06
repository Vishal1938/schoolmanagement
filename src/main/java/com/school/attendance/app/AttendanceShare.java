package com.school.attendance.app;

/**
 * How much of a date range a student was present for, as other modules see it — the one line a
 * report card prints.
 *
 * <p>Deliberately narrower than the attendance history: a percentage and the number of days it was
 * worked out from, not the day-by-day record. {@code markedDays} is the days that were actually
 * marked, excluding LEAVE, so a term nobody took the register for reads as "0 of 0 days" rather than
 * as everybody being absent.
 *
 * @param markedDays the denominator of {@code percentage}
 * @param percentage {@code 0} when no day counted
 */
public record AttendanceShare(int markedDays, double percentage) {

	/** Used when there is no range to measure — a session without dates, which B4 should not allow. */
	public static AttendanceShare none() {
		return new AttendanceShare(0, 0);
	}
}

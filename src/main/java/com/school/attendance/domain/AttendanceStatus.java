package com.school.attendance.domain;

/**
 * How a student or employee was marked on one day.
 *
 * <p>Only {@link #LEAVE} is excluded from the attendance percentage: approved leave is neither
 * present nor a blemish, so it leaves the denominator rather than counting against it. The weights
 * are in {@link #presenceWeight()}.
 */
public enum AttendanceStatus {

	PRESENT(1.0),

	ABSENT(0.0),

	/** Came, but late. Counts as a full day's presence; the record of lateness is the point. */
	LATE(1.0),

	/** Half the day. Counts as half. */
	HALF_DAY(0.5),

	/** Approved leave. Excluded from the percentage entirely — see {@link #countsTowardsPercentage()}. */
	LEAVE(0.0);

	private final double presenceWeight;

	AttendanceStatus(double presenceWeight) {
		this.presenceWeight = presenceWeight;
	}

	/** What this status contributes to the numerator of the percentage. */
	public double presenceWeight() {
		return presenceWeight;
	}

	/** Whether this status contributes to the denominator. False only for {@link #LEAVE}. */
	public boolean countsTowardsPercentage() {
		return this != LEAVE;
	}
}

package com.school.ai.domain;

/**
 * A reason a student shows up on the at-risk list.
 *
 * <p><strong>Every one of these is computed in code, from the school's own records.</strong> The
 * model is shown the flags and asked to put them in a sentence; it is never asked which students are
 * at risk, and it cannot add, drop or alter a flag. A thresholds decision is a school's policy, and
 * policy does not belong in a prompt.
 */
public enum InsightFlag {

	/** Under 75% of the marked days of the current session. */
	LOW_ATTENDANCE,

	/**
	 * The percentage in the latest published exam is 15 or more points below the one before it. In
	 * points, not relative: 70% to 55% counts, 20% to 17% does not.
	 */
	MARKS_DROPPED,

	/** At least one subject failed, absent or unmarked in the latest published exam. */
	SUBJECT_FAILED,

	/** Something still owed after its due date plus the school's grace days. */
	FEES_OVERDUE
}

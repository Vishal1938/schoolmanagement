package com.school.exams.domain;

/**
 * Where an exam is in its life. The status is what gates everything else: the schedule is only
 * editable in {@link #DRAFT}, marks only in {@link #MARKS_ENTRY}, and results only readable once
 * {@link #PUBLISHED}.
 */
public enum ExamStatus {

	/** Being set up. The schedule can still change; no marks exist yet. */
	DRAFT,

	/** The schedule is fixed and teachers are entering marks. Results are not visible to students. */
	MARKS_ENTRY,

	/**
	 * Results are out. Reached only once every student has a mark for every subject, so a published
	 * exam cannot show a student a half-finished report card.
	 */
	PUBLISHED
}

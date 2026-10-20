package com.school.fees.api;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /fees/structures/semester/suggest}: two semesters filled in, ready to be reviewed and
 * posted back to {@code POST /fees/structures/semester}.
 *
 * <p>This is a <strong>suggestion and nothing else</strong>. It reads no fee data, writes nothing, and
 * every field in it is meant to be edited: a college that runs January-to-June first, or whose second
 * semester falls due in February, changes the dates and posts. It exists because the alternative is an
 * office typing four dates and two labels per course for forty courses, which is where the mistakes
 * come from.
 *
 * <p>The numbers come from the course name. {@code "B.Com Year 2"} suggests semesters 3 and 4, because
 * the third and fourth semesters of a programme are its second year; a name with no year in it falls
 * back to 1 and 2. The convention is the common Indian one and the derivation is deliberately shallow —
 * a course named anything else gets 1 and 2 and the office corrects it.
 *
 * @param derivedFromCourseYear the year read out of the course name, or null when none was found and
 *                              the numbers fell back to 1 and 2. Here so a screen can say "we guessed"
 *                              rather than presenting the numbers as fact
 */
public record SemesterSuggestionResponse(
		String sessionId,
		String classId,
		String className,
		Integer derivedFromCourseYear,
		List<Semester> semesters) {

	/** Exactly the shape {@code SemesterFeeSetupRequest.Semester} takes, so it can be posted straight back. */
	public record Semester(
			int number,
			String label,
			LocalDate periodStart,
			LocalDate periodEnd,
			LocalDate dueDate) {
	}
}

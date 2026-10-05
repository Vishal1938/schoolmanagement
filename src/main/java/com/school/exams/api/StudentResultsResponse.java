package com.school.exams.api;

import java.util.List;

/**
 * Every exam result a student has in one session.
 *
 * <p>A student reading their own only ever sees PUBLISHED exams. An admin or teacher sees exams in
 * MARKS_ENTRY too, so they can check a result sheet before releasing it.
 */
public record StudentResultsResponse(
		String uniqueId,
		String name,
		String className,
		String section,
		String sessionId,
		List<ExamResult> exams) {
}

package com.school.exams.api;

import java.util.List;

import com.school.exams.domain.ExamStatus;

/**
 * One class-section's result sheet for one exam: a row per student, best rank first.
 *
 * <p>The columns are the exam's schedule, so every row has a mark slot for every paper even where
 * no mark was entered.
 */
public record ClassResultSheetResponse(
		String examId,
		String examName,
		ExamStatus status,
		String classId,
		String className,
		String section,
		List<Column> subjects,
		List<Row> rows) {

	public record Column(String subjectId, String subjectName, int maxMarks, int passMarks) {
	}

	/**
	 * @param marks one entry per column, in the same order; null where the paper was not marked or
	 *              the student was absent
	 */
	public record Row(
			String studentUniqueId,
			String name,
			int rollNo,
			List<Integer> marks,
			int total,
			int maxTotal,
			double percentage,
			String grade,
			int rank,
			ResultOutcome result) {
	}
}

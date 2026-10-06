package com.school.exams.api;

import java.util.List;

/**
 * One student's result in one exam.
 *
 * <p>How the numbers are arrived at:
 * <ul>
 * <li>{@code total} sums the marks obtained; an absent or unmarked paper contributes 0.</li>
 * <li>{@code maxTotal} sums {@code maxMarks} over <em>every</em> paper in the schedule, marked or
 *     not, so a missing entry lowers the percentage rather than quietly shrinking the exam.</li>
 * <li>{@code percentage} is {@code total / maxTotal × 100} to two decimals.</li>
 * <li>{@code grade} is the band the <strong>floored</strong> percentage falls into — 89.6% is
 *     graded as 89, because a band that starts at 90 means ninety.</li>
 * <li>{@code result} is PASS only when every subject passed.</li>
 * <li>{@code rank} is within the student's class-section, by total, highest first. Ties share a
 *     rank and the next rank skips: 1, 2, 2, 4.</li>
 * </ul>
 *
 * @param grade null when the grading scheme has no band for that percentage
 */
public record ExamResult(
		String examId,
		String examName,
		List<SubjectResult> subjects,
		int total,
		int maxTotal,
		double percentage,
		String grade,
		String remark,
		int rank,
		int rankOutOf,
		ResultOutcome result) {

	/**
	 * @param marks null when the paper was not marked, or the student was absent
	 * @param pass  false when absent, unmarked, or below {@code passMarks}
	 * @param grade null when absent or unmarked, or when no band covers the percentage
	 */
	public record SubjectResult(
			String subjectId,
			String subjectName,
			Integer marks,
			int maxMarks,
			int passMarks,
			boolean absent,
			String grade,
			boolean pass,
			String remarks) {
	}
}

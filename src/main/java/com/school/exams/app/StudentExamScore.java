package com.school.exams.app;

import java.time.LocalDate;
import java.util.List;

/**
 * How one student did in one published exam, as other modules see it.
 *
 * <p>Deliberately narrower than {@code ExamResult}, which is the report card: <strong>no rank and no
 * rank-out-of</strong>. The AI features in B18 are the only caller, and neither a remark nor an
 * at-risk flag is allowed to turn on where a child placed in their class — that is between the
 * school and the family, and a sentence written by a model is not where it should surface.
 *
 * <p>Leaving rank out is also what makes a whole class affordable to read: ranking one student means
 * scoring every one of their classmates, so computing it per row would re-read the section once per
 * student.
 *
 * @param lastPaperOn the date of the last paper in the schedule, which is the order exams are read
 *                    in — a session's exams are named, not dated, so "the previous exam" can only
 *                    mean the one whose papers finished earlier. Null when no paper carries a date
 * @param percentage  marks obtained over the whole schedule, to two decimals; an unmarked or absent
 *                    paper lowers it rather than shrinking the exam
 * @param grade       the band the floored percentage falls into, or null when the scheme has no band
 * @param passed      true only when every subject was passed
 */
public record StudentExamScore(
		String examId,
		String examName,
		LocalDate lastPaperOn,
		double percentage,
		String grade,
		boolean passed,
		List<SubjectScore> subjects) {

	/**
	 * One paper of that exam.
	 *
	 * @param marks  null when the paper was not marked, or the student was absent
	 * @param passed false when absent, unmarked, or below the paper's pass mark
	 */
	public record SubjectScore(
			String subjectName,
			Integer marks,
			int maxMarks,
			String grade,
			boolean passed,
			boolean absent) {
	}

	/** The subjects that were failed, by name — what a remark or a flag actually talks about. */
	public List<String> failedSubjects() {
		return subjects.stream().filter(subject -> !subject.passed()).map(SubjectScore::subjectName).toList();
	}
}

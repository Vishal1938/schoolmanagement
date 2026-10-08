package com.school.quiz.api;

import java.time.Instant;
import java.util.List;

/**
 * The marked paper, as {@code PUT /quizzes/attempts/{attemptId}} returns it.
 *
 * <p>{@link #questions} is <strong>null unless the quiz has {@code showAnswersAfterSubmit}</strong>.
 * That flag is the teacher's decision about whether the answer key may leave the building — a quiz
 * the rest of the class has not sat yet should not be reviewable by the first student to finish — so
 * when it is off, the student gets their score and nothing that reconstructs the key.
 *
 * @param score     what this attempt earned
 * @param maxScore  what a perfect attempt would have earned
 * @param questions the per-question review, or null when the quiz does not show answers
 */
public record AttemptResultResponse(
		String attemptId,
		String quizId,
		String title,
		int score,
		int maxScore,
		Instant submittedAt,
		List<Question> questions) {

	/**
	 * One question as reviewed afterwards.
	 *
	 * @param selected what the student picked, by option id
	 * @param correct  the keyed answer, by option id
	 * @param wasRight whether the two matched exactly, which is also whether {@code marksAwarded} is full
	 */
	public record Question(
			String id,
			String text,
			List<Option> options,
			List<String> selected,
			List<String> correct,
			boolean wasRight,
			int marks,
			int marksAwarded,
			String explanation) {
	}

	public record Option(String id, String text) {
	}
}

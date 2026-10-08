package com.school.quiz.api;

import java.time.Instant;
import java.util.List;

/**
 * The results sheet for one quiz, as {@code GET /quizzes/{id}/results} returns it.
 *
 * <p>Built from the class roll rather than from the attempts, which is why {@link #notAttempted} can
 * exist at all: a sheet assembled from submissions can only ever say who sat the quiz, and the
 * question a teacher actually has is who did not.
 *
 * @param students     everybody on the roll who started at least one attempt, best score first
 * @param notAttempted everybody on the roll who did not, in roll order
 * @param questions    per-question accuracy over the submitted attempts
 * @param rollCount    how many students the quiz was set for, so the two lists can be read as shares
 */
public record QuizResultsResponse(
		String quizId,
		String title,
		String classId,
		List<String> sections,
		int maxScore,
		int rollCount,
		int attemptedCount,
		long submittedAttempts,
		Double averageScore,
		List<StudentResult> students,
		List<StudentRow> notAttempted,
		List<QuestionAccuracy> questions) {

	/**
	 * One student's result.
	 *
	 * @param attempts    how many they started, abandoned ones included
	 * @param bestScore   the best over their submitted attempts, or null if none is submitted yet
	 * @param submittedAt when the best attempt was submitted
	 */
	public record StudentResult(
			String uniqueId,
			String name,
			String section,
			int rollNo,
			int attempts,
			Integer bestScore,
			int maxScore,
			Instant submittedAt) {
	}

	/** A student on the roll with nothing to show. */
	public record StudentRow(
			String uniqueId,
			String name,
			String section,
			int rollNo) {
	}

	/**
	 * How one question went.
	 *
	 * <p>The denominator is the number of <strong>submitted attempts</strong>, not the number that
	 * answered: a question left blank is a question got wrong, and counting only the students who tried
	 * it would flatter a question most of the class ran out of time for.
	 *
	 * @param accuracyPercent correct answers as a percentage of submitted attempts, one decimal place.
	 *                        Null when nobody has submitted yet
	 */
	public record QuestionAccuracy(
			String id,
			String text,
			int marks,
			long correctCount,
			long answeredCount,
			long attemptCount,
			Double accuracyPercent) {
	}
}

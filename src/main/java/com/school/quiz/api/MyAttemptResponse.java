package com.school.quiz.api;

import java.time.Instant;

import com.school.quiz.domain.QuizAttempt;

/**
 * A row of {@code GET /quizzes/attempts/mine}: one past sitting, with what it scored.
 *
 * <p>No questions and no answers — this is a history list. A student who wants to review a quiz that
 * allows it re-reads the submission response; a quiz that does not allow it is not reviewable here
 * either.
 *
 * @param submittedAt   null while the attempt is still in flight
 * @param autoSubmitted true when the deadline passed with nothing submitted, which is a zero the
 *                      student should be able to tell apart from a zero they earned
 */
public record MyAttemptResponse(
		String attemptId,
		String quizId,
		String title,
		int attemptNo,
		Instant startedAt,
		Instant deadline,
		Instant submittedAt,
		boolean autoSubmitted,
		boolean submitted,
		int score,
		int maxScore) {

	public static MyAttemptResponse of(QuizAttempt attempt) {
		return new MyAttemptResponse(
				attempt.getId(),
				attempt.getQuizId(),
				attempt.getQuizTitle(),
				attempt.getAttemptNo(),
				attempt.getStartedAt(),
				attempt.getDeadline(),
				attempt.getSubmittedAt(),
				attempt.isAutoSubmitted(),
				attempt.isSubmitted(),
				attempt.getScore(),
				attempt.getMaxScore());
	}
}

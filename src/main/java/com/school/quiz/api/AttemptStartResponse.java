package com.school.quiz.api;

import java.time.Instant;
import java.util.List;

import com.school.quiz.domain.QuestionType;

/**
 * The paper, as handed to a student by {@code POST /quizzes/{id}/attempts}.
 *
 * <p><strong>There is no answer key in this record and there is no field that could hold one.</strong>
 * {@code correctOptionIds} and {@code explanation} are absent by construction rather than nulled out
 * on the way past, so no future edit to the mapping can leak them into a live attempt.
 *
 * <p>{@link #questions} are in this attempt's own order — shuffled if the quiz says so — and that
 * order is stored on the attempt, so resuming after a refresh returns the same paper rather than a
 * freshly shuffled one.
 *
 * @param deadline   the server's last word on when this attempt ends: the earlier of the time limit
 *                   and the quiz's {@code endAt}. Submissions are taken up to 30 seconds past it
 * @param resumed    true when this is an attempt that was already in flight, not a new one
 * @param attemptNo  1-based, so the student can see "attempt 2 of 3"
 */
public record AttemptStartResponse(
		String attemptId,
		String quizId,
		String title,
		String description,
		int timeLimitMinutes,
		Instant startedAt,
		Instant deadline,
		int attemptNo,
		int maxAttempts,
		boolean resumed,
		int totalMarks,
		List<Question> questions) {

	/** A question with its options and its marks, and nothing that gives the answer away. */
	public record Question(
			String id,
			QuestionType type,
			String text,
			List<Option> options,
			int marks) {
	}

	public record Option(String id, String text) {
	}
}

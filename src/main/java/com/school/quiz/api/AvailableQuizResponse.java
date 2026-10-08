package com.school.quiz.api;

import java.time.Instant;

import com.school.quiz.domain.QuizWindow;

/**
 * A row of {@code GET /quizzes/available}: what a student sees before starting.
 *
 * <p><strong>No questions.</strong> This is the only list a student gets, and the questions are
 * handed over by {@code POST /quizzes/{id}/attempts} — which is also the call that starts the clock.
 * Putting them here would let a student read the paper, think about it, and start the timer
 * afterwards.
 *
 * @param status        where the clock is: UPCOMING, OPEN or ENDED
 * @param attemptsUsed  how many of {@code maxAttempts} are gone, started ones included
 * @param bestScore     the best score over this student's submitted attempts, or null if there are none
 * @param canStart      whether {@code POST .../attempts} would succeed right now: open, and attempts left
 * @param inProgress    true when an attempt is still in flight, so the button says "resume"
 */
public record AvailableQuizResponse(
		String id,
		String title,
		String description,
		String subjectId,
		String classId,
		int timeLimitMinutes,
		Instant startAt,
		Instant endAt,
		int maxAttempts,
		int questionCount,
		int totalMarks,
		QuizWindow status,
		int attemptsUsed,
		Integer bestScore,
		boolean canStart,
		boolean inProgress) {
}

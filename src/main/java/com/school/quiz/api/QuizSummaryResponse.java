package com.school.quiz.api;

import java.time.Instant;
import java.util.List;

import com.school.quiz.domain.Quiz;
import com.school.quiz.domain.QuizStatus;

/**
 * A row of {@code GET /quizzes}: a quiz without its questions.
 *
 * <p>The list is a teacher's own board and an admin's whole board, and neither needs 40 questions and
 * their answer key per row. {@code questionCount} and {@code totalMarks} are what the list actually
 * shows; the questions come from {@code GET /quizzes/{id}}.
 *
 * @param attemptCount how many attempts have been started, so a teacher can see uptake at a glance
 */
public record QuizSummaryResponse(
		String id,
		String title,
		String subjectId,
		String classId,
		List<String> sections,
		int timeLimitMinutes,
		Instant startAt,
		Instant endAt,
		int maxAttempts,
		QuizStatus status,
		int questionCount,
		int totalMarks,
		long attemptCount,
		String createdBy,
		String createdByName,
		Instant createdAt) {

	public static QuizSummaryResponse of(Quiz quiz, long attemptCount) {
		return new QuizSummaryResponse(
				quiz.getId(),
				quiz.getTitle(),
				quiz.getSubjectId(),
				quiz.getClassId(),
				quiz.getSections() == null ? List.of() : quiz.getSections(),
				quiz.getTimeLimitMinutes(),
				quiz.getStartAt(),
				quiz.getEndAt(),
				quiz.getMaxAttempts(),
				quiz.getStatus(),
				quiz.questionCount(),
				quiz.totalMarks(),
				attemptCount,
				quiz.getCreatedBy(),
				quiz.getCreatedByName(),
				quiz.getCreatedAt());
	}
}

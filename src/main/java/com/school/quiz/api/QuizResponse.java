package com.school.quiz.api;

import java.time.Instant;
import java.util.List;

import com.school.quiz.domain.QuestionType;
import com.school.quiz.domain.Quiz;
import com.school.quiz.domain.QuizQuestion;
import com.school.quiz.domain.QuizStatus;

/**
 * A quiz <strong>including its answer key</strong>, as {@code GET /quizzes/{id}} returns it.
 *
 * <p>This is the author's view and nothing else. It is reachable only with {@code QUIZ_MANAGE}, and
 * only for an admin or the teacher who wrote the quiz; a student's view of the same questions is
 * {@code AttemptStartResponse}, which is a different record precisely so that stripping the key is
 * not a flag somebody can forget to pass.
 */
public record QuizResponse(
		String id,
		String title,
		String description,
		String subjectId,
		String classId,
		List<String> sections,
		int timeLimitMinutes,
		Instant startAt,
		Instant endAt,
		int maxAttempts,
		boolean shuffleQuestions,
		boolean showAnswersAfterSubmit,
		QuizStatus status,
		int questionCount,
		int totalMarks,
		List<Question> questions,
		String createdBy,
		String createdByName,
		Instant createdAt,
		Instant updatedAt,
		Instant publishedAt,
		Instant closedAt) {

	public record Question(
			String id,
			QuestionType type,
			String text,
			List<Option> options,
			List<String> correctOptionIds,
			int marks,
			String explanation) {
	}

	public record Option(String id, String text) {
	}

	public static QuizResponse of(Quiz quiz) {
		return new QuizResponse(
				quiz.getId(),
				quiz.getTitle(),
				quiz.getDescription(),
				quiz.getSubjectId(),
				quiz.getClassId(),
				quiz.getSections() == null ? List.of() : quiz.getSections(),
				quiz.getTimeLimitMinutes(),
				quiz.getStartAt(),
				quiz.getEndAt(),
				quiz.getMaxAttempts(),
				quiz.isShuffleQuestions(),
				quiz.isShowAnswersAfterSubmit(),
				quiz.getStatus(),
				quiz.questionCount(),
				quiz.totalMarks(),
				quiz.getQuestions() == null ? List.of() : quiz.getQuestions().stream()
						.map(QuizResponse::toQuestion)
						.toList(),
				quiz.getCreatedBy(),
				quiz.getCreatedByName(),
				quiz.getCreatedAt(),
				quiz.getUpdatedAt(),
				quiz.getPublishedAt(),
				quiz.getClosedAt());
	}

	private static Question toQuestion(QuizQuestion question) {
		return new Question(
				question.id(),
				question.type(),
				question.text(),
				question.options() == null ? List.of() : question.options().stream()
						.map(option -> new Option(option.id(), option.text()))
						.toList(),
				question.correctOptionIds() == null ? List.of() : question.correctOptionIds(),
				question.marks(),
				question.explanation());
	}
}

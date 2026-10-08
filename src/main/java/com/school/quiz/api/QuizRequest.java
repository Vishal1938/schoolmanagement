package com.school.quiz.api;

import java.time.Instant;
import java.util.List;

import com.school.quiz.domain.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A quiz as written by {@code POST /quizzes} or {@code PUT /quizzes/{id}}.
 *
 * <p>Both calls replace the whole quiz, questions included. There is no per-question endpoint: a
 * quiz is only editable while it is a {@code DRAFT}, so there is nothing to merge against, and
 * sending the whole thing means the client never has to reason about which half of its edits landed.
 *
 * <p>What Bean Validation cannot express is checked in {@code QuizService}: that the subject and
 * class exist, that the sections belong to the class, that option ids are unique within a question
 * and that every keyed answer is one of that question's options, that the type's option and answer
 * arity holds, and that {@code endAt} is after {@code startAt}. Completeness — at least one question,
 * every question keyed, both dates set — is checked at publication instead, because a draft is
 * allowed to be half-written.
 *
 * @param sections               empty or absent means every section of the class
 * @param startAt                optional in a draft; required to publish
 * @param endAt                  likewise, and must be after {@code startAt}
 * @param maxAttempts            null means 1
 * @param showAnswersAfterSubmit whether a submission comes back with the key and the explanations
 * @param questions              may be empty while the quiz is a draft
 */
public record QuizRequest(
		@NotBlank @Size(max = 200) String title,
		@Size(max = 4_000) String description,
		@NotBlank @Size(max = 64) String subjectId,
		@NotBlank @Size(max = 64) String classId,
		@Size(max = 26) List<@NotBlank @Size(max = 8) String> sections,
		@Min(1) @Max(600) int timeLimitMinutes,
		Instant startAt,
		Instant endAt,
		@Min(1) @Max(20) Integer maxAttempts,
		boolean shuffleQuestions,
		boolean showAnswersAfterSubmit,
		@Size(max = 200) List<@Valid Question> questions) {

	/**
	 * One question and its answer key.
	 *
	 * @param id               leave null on a new question and one is generated. Send the id back on an
	 *                         edit to keep it — ids are what attempts refer to
	 * @param correctOptionIds may be empty while the quiz is a draft; publication refuses an empty one.
	 *                         MCQ_SINGLE and TRUE_FALSE take at most one
	 * @param marks            null means 1
	 */
	public record Question(
			@Size(max = 64) String id,
			@NotNull QuestionType type,
			@NotBlank @Size(max = 2_000) String text,
			@NotNull @Size(min = 2, max = 10) List<@Valid Option> options,
			@Size(max = 10) List<@NotBlank @Size(max = 64) String> correctOptionIds,
			@Min(1) @Max(100) Integer marks,
			@Size(max = 2_000) String explanation) {
	}

	/**
	 * @param id leave null on a new option and one is generated; send it back to keep it
	 */
	public record Option(
			@Size(max = 64) String id,
			@NotBlank @Size(max = 500) String text) {
	}
}

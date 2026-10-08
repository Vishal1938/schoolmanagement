package com.school.quiz.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A submission, as sent to {@code PUT /quizzes/attempts/{attemptId}}.
 *
 * <p>The whole paper in one call. Questions left out are treated as skipped and score zero — so a
 * client may simply send what the student touched — but an unknown {@code questionId}, a repeated
 * one, or an option id that does not belong to its question is rejected rather than ignored: each
 * means the client and the server disagree about what paper is being marked.
 *
 * @param answers may be empty, which is a blank paper and a legitimate thing to submit
 */
public record SubmitAttemptRequest(
		@NotNull @Size(max = 200) List<@Valid Answer> answers) {

	/**
	 * @param selectedOptionIds what the student picked. Empty means the question was skipped; more than
	 *                          one is only valid on an MCQ_MULTI
	 */
	public record Answer(
			@NotBlank @Size(max = 64) String questionId,
			@NotNull @Size(max = 10) List<@NotBlank @Size(max = 64) String> selectedOptionIds) {
	}
}

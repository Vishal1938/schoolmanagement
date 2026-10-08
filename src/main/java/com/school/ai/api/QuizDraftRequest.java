package com.school.ai.api;

import com.school.ai.domain.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What to draft questions about, for {@code POST /ai/quiz-draft}.
 *
 * @param subjectId must exist; its name and the class's go into the prompt, so a model drafting for
 *                  "Mathematics, Class 7" is told as much rather than left to guess from the topic
 * @param count     how many questions to aim for, 15 at the most. Fewer may come back: an invalid
 *                  question is dropped rather than patched, and the response says how many were
 * @param typeMix   optional. Absent means every question is {@code MCQ_SINGLE}
 */
public record QuizDraftRequest(
		@NotBlank @Size(max = 64) String subjectId,
		@NotBlank @Size(max = 64) String classId,
		@NotBlank @Size(max = 200) String topic,
		@Min(1) @Max(15) int count,
		@NotNull Difficulty difficulty,
		@Valid TypeMix typeMix) {

	/**
	 * Roughly how many questions of each kind are wanted.
	 *
	 * <p>Read as proportions rather than as exact counts: {@code count} is what decides the length of
	 * the draft, and these are scaled to it. {@code {mcqSingle: 2, trueFalse: 1}} with
	 * {@code count: 9} therefore asks for six single-answer questions and three true/false, and so
	 * does {@code {mcqSingle: 6, trueFalse: 3}}. Absent or all-zero means all {@code MCQ_SINGLE}.
	 *
	 * <p>Even scaled, the mix is a request and not a guarantee — a model asked for three true/false
	 * questions on a topic that has none worth asking may well return something else, and a question
	 * is kept or dropped on whether it is well-formed, never on whether it is the type ordered.
	 */
	public record TypeMix(
			@Min(0) @Max(15) Integer mcqSingle,
			@Min(0) @Max(15) Integer mcqMulti,
			@Min(0) @Max(15) Integer trueFalse) {
	}
}

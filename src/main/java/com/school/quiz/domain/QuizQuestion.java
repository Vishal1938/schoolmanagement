package com.school.quiz.domain;

import java.util.List;

/**
 * One question, embedded in its quiz.
 *
 * <p>Embedded rather than a collection of its own because a question has no life outside the quiz it
 * was written for: it is never queried, shared or re-used, and loading a quiz should be one read.
 *
 * <p><strong>{@link #correctOptionIds} and {@link #explanation} are the answer key.</strong> They
 * live on the same document as the question text, so every path that hands a question to a student
 * has to strip them — which is why there is exactly one such path, and why no response DTO in this
 * module carries both the question text and the key except the teacher's.
 *
 * @param id              stable for the life of the quiz; an attempt's answers refer to it
 * @param correctOptionIds the keyed answer, as a set of option ids. Empty is allowed while the quiz is
 *                        a draft, and is what publication refuses
 * @param marks           awarded in full for an exact match and not at all otherwise
 * @param explanation     shown after submission when the quiz allows it. Optional
 */
public record QuizQuestion(
		String id,
		QuestionType type,
		String text,
		List<QuestionOption> options,
		List<String> correctOptionIds,
		int marks,
		String explanation) {
}

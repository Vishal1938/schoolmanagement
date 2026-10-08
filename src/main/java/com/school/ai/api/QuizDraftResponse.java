package com.school.ai.api;

import java.util.List;

import com.school.ai.domain.Difficulty;
import com.school.quiz.domain.QuestionType;

/**
 * A draft quiz. <strong>Nothing here has been saved.</strong>
 *
 * <p>{@link Question} is field-for-field the question object {@code POST /quizzes} takes, so a
 * teacher's client can put {@code questions} straight into a quiz body, edit what it likes and save
 * when it is happy. That is the whole point of the endpoint: the model writes a first draft and a
 * teacher stays the author.
 *
 * <p>Ids are generated here rather than by the model, which has no notion of a stable identifier and
 * would be asked to keep two lists in agreement for nothing. They are positional and provisional —
 * {@code POST /quizzes} mints its own on save — and only exist so {@code correctOptionIds} has
 * something to point at.
 *
 * @param requested what the request asked for
 * @param dropped   how many questions came back malformed and were discarded; {@code questions.size()
 *                  + dropped} need not equal {@code requested}, because a model asked for ten may
 *                  simply write nine
 */
public record QuizDraftResponse(
		String subjectId,
		String subjectName,
		String classId,
		String className,
		String topic,
		Difficulty difficulty,
		int requested,
		int dropped,
		List<Question> questions) {

	/**
	 * One drafted question and its answer key.
	 *
	 * <p>{@code marks} is always 1. A draft has no idea what a question is worth in the quiz it ends
	 * up in, and one mark each is what a teacher adjusts from rather than a number a model invented.
	 */
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
}

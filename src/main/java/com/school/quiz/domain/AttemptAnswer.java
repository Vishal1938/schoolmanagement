package com.school.quiz.domain;

import java.util.List;

/**
 * What one student chose on one question, and what it earned.
 *
 * <p>{@code correct} and {@code marksAwarded} are the grader's verdict, stored rather than recomputed
 * on read. A quiz is frozen once published, so the two can never disagree — and storing them means
 * the results endpoint can total a class without re-grading every attempt against the answer key.
 *
 * @param selectedOptionIds what the student picked; empty for a question they skipped
 * @param correct           whether the selection matched the key exactly
 * @param marksAwarded      the question's full marks, or zero. There is no partial credit
 */
public record AttemptAnswer(
		String questionId,
		List<String> selectedOptionIds,
		boolean correct,
		int marksAwarded) {
}

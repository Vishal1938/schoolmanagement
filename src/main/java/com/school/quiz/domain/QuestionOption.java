package com.school.quiz.domain;

/**
 * One choice on a question.
 *
 * <p>The {@code id} is what an answer refers to, never the position: questions are shuffled per
 * attempt, so "the second option" means nothing by the time a submission comes back. Ids are unique
 * within their question only, which is all a lookup ever needs.
 *
 * @param id   stable for the life of the quiz; supplied by the author or generated on write
 * @param text what the student reads
 */
public record QuestionOption(String id, String text) {
}

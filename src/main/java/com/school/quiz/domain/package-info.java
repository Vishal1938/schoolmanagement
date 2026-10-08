/**
 * Documents and enums for the quiz module.
 *
 * <p>Exposed as a named interface for one type only: {@link com.school.quiz.domain.QuestionType}. The
 * AI quiz draft in B18 hands back questions a teacher pastes straight into {@code POST /quizzes}, so
 * the two have to agree on what {@code MCQ_SINGLE} is spelled like — and they agree by sharing the
 * enum rather than by two copies of it drifting apart.
 *
 * <p>Nothing else here is meant for other modules: {@code Quiz}, {@code QuizQuestion} and
 * {@code QuizAttempt} carry answer keys, and the repositories stay in {@code infra}.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.school.quiz.domain;

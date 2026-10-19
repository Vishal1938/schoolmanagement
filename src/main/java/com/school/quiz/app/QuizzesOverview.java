package com.school.quiz.app;

/**
 * How many quizzes are in each state, as the admin dashboard reads it.
 *
 * <p>{@code published} is the one that matters on a dashboard — a quiz a class can sit right now.
 * Drafts are somebody's work in progress and closed ones are history; both are here only so the
 * three add up to what the quizzes screen lists.
 */
public record QuizzesOverview(long draft, long published, long closed) {
}

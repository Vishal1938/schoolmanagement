package com.school.ai.domain;

/**
 * How hard a drafted quiz should be.
 *
 * <p>Three bands rather than a number, because this is a word in a prompt and nothing else: it never
 * reaches a database, and a model cannot tell 6/10 from 7/10 in any way a teacher would recognise.
 */
public enum Difficulty {

	/** Recall and definitions, straight from the lesson. */
	EASY,

	/** Applying the idea to a familiar situation. */
	MEDIUM,

	/** Multi-step reasoning, or distinguishing between two things that look alike. */
	HARD
}

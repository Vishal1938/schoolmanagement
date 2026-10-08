package com.school.quiz.domain;

/**
 * Where a quiz is in its life.
 *
 * <p>The only transitions are {@code DRAFT → PUBLISHED → CLOSED}, and neither is reversible. That is
 * deliberate: unpublishing a quiz students have already sat would leave attempts pointing at a
 * question set nobody can see, and reopening a closed one would let marks move after they were read.
 * A quiz that needs changing after publication is replaced by a new one.
 */
public enum QuizStatus {

	/**
	 * Being written. Invisible to students, editable and deletable. A draft may be incomplete — no
	 * questions, no answer key, no dates — because that is what writing one looks like.
	 */
	DRAFT,

	/**
	 * Visible to the class it was set for, and sittable inside its {@code startAt}–{@code endAt}
	 * window. Frozen: publishing is the point at which the question set stops moving.
	 */
	PUBLISHED,

	/**
	 * Finished with. No new attempts, whatever the window says, and it drops off the students' list.
	 * Results stay readable — closing is how a teacher stops a quiz early, not how they delete it.
	 */
	CLOSED
}

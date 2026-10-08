package com.school.quiz.domain;

import java.time.Instant;

/**
 * Where "now" sits relative to a published quiz's window. Derived on every read rather than stored,
 * because it changes without anybody touching the quiz — a stored copy would be stale the moment the
 * clock passed {@code startAt}, and no scheduled job should be needed to tell a student that a quiz
 * has opened.
 */
public enum QuizWindow {

	/** Published, but not yet open. The student can see it coming and cannot start it. */
	UPCOMING,

	/** Open now. The only window in which an attempt may be started. */
	OPEN,

	/** Past {@code endAt}. Attempts already in flight run to their own deadline; no new ones start. */
	ENDED;

	/**
	 * @param startAt the quiz's opening instant, which a draft may not have set yet
	 * @param endAt   its closing instant, likewise
	 * @return the window, treating a quiz with no dates as {@link #UPCOMING} — unset is not open
	 */
	public static QuizWindow at(Instant now, Instant startAt, Instant endAt) {
		if (startAt == null || endAt == null || now.isBefore(startAt)) {
			return UPCOMING;
		}
		return now.isBefore(endAt) ? OPEN : ENDED;
	}
}

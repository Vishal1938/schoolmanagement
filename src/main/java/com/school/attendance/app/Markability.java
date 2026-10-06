package com.school.attendance.app;

/**
 * Whether a register may be submitted or corrected right now, and if not, why — in words that can
 * be shown to the user.
 *
 * <p>Carried rather than thrown so one piece of logic can serve both jobs: the GET reports it as
 * {@code editable}/{@code lockedReason} so the UI can show a read-only register, and the PUT turns
 * the same answer into a 422.
 *
 * @param reason null when {@code allowed}
 */
record Markability(boolean allowed, String reason) {

	private static final Markability PERMITTED = new Markability(true, null);

	static Markability permitted() {
		return PERMITTED;
	}

	static Markability refused(String reason) {
		return new Markability(false, reason);
	}
}

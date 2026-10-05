package com.school.common.id;

/**
 * The kinds of human-readable unique ID this deployment issues, as defined in API_CONTRACT.md §4:
 * {@code {SCHOOL_CODE}-{TYPE}-{YY}-{SEQ}}.
 *
 * <p>Teachers, staff and admins all share {@link #EMP}: the role lives on the user account, not in
 * the ID, so a staff member who later teaches keeps the same ID.
 */
public enum IdType {

	/** Students, five-digit sequence. */
	STU(5),

	/** Employees — teachers, staff and admins — four-digit sequence. */
	EMP(4);

	private final int sequenceWidth;

	IdType(int sequenceWidth) {
		this.sequenceWidth = sequenceWidth;
	}

	/** Digits the sequence is zero-padded to, e.g. 5 gives {@code 00042}. */
	public int sequenceWidth() {
		return sequenceWidth;
	}
}

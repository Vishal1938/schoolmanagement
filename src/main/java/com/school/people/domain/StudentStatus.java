package com.school.people.domain;

/**
 * Where a student stands with the school. Students are never deleted — attendance, marks, invoices
 * and receipts all point at them, and a receipt has to stay readable years later — so leaving is a
 * status change.
 */
public enum StudentStatus {

	/** On the rolls. */
	ACTIVE,

	/** Withdrawn mid-schooling: transferred out, or simply stopped coming. */
	LEFT,

	/** Finished the final class. Set in bulk by the promotion run (B5 part 2). */
	ALUMNI
}

package com.school.common.fees;

/**
 * How a student stands on their fees, with no amount attached.
 *
 * <p>This is the whole of what a teacher may learn about a student's fees (CLAUDE.md rule 2): the
 * status says whether to chase the family, which is all a class teacher needs, while invoices,
 * amounts and payment history stay behind {@code FEE_READ_FULL}.
 */
public enum FeeStatus {

	/** Nothing outstanding — including a student who has no invoices yet. */
	PAID,

	/** Something has been paid, something is still due, and nothing has passed its due date. */
	PARTIAL,

	/** Nothing paid yet, but the due date has not passed. */
	DUE,

	/**
	 * Something is still owed after its due date plus the grace days. Takes precedence over
	 * {@link #PARTIAL}: it is the actionable one, and a part-paid invoice that is late is still late.
	 */
	OVERDUE
}

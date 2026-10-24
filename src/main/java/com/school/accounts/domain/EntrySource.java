package com.school.accounts.domain;

/**
 * Who wrote the entry, and therefore where it may be corrected.
 *
 * <p>Only {@link #MANUAL} entries are editable and deletable here. The rest are the shadow of
 * something recorded elsewhere — a fee payment, a salary, an advance — and the way to change one is
 * to change the thing it came from, in the module that owns it. Letting the books be edited directly
 * would make the expense report and the fee register disagree with no way to tell which is right.
 */
public enum EntrySource {

	/** Somebody typed it in. The only kind that {@code PUT} and {@code DELETE} accept. */
	MANUAL,

	/** Written from a captured fee payment. */
	FEES,

	/** Written from a salary that was paid out. */
	PAYROLL,

	/** Written from an advance being given, or an installment of one coming back. */
	ADVANCE,

	/**
	 * A transfer between two of the school's own accounts.
	 *
	 * <p><strong>Nothing carries this today.</strong> A transfer is neither income nor expense, so it
	 * is kept in {@link AccountTransfer} rather than as a pair of entries every report would then have
	 * to remember to exclude; the balances read both collections. The constant is here because
	 * {@code source} is a closed vocabulary in API_CONTRACT.md and a client filtering on it should be
	 * able to name the value, and because booking transfers as entry pairs is the obvious way this
	 * would grow.
	 */
	TRANSFER
}

package com.school.accounts.domain;

/**
 * Whether the money has actually moved yet.
 *
 * <p>{@link #UNPAID} is <strong>only ever an expense</strong>: it is a bill the school has received
 * and not yet settled, which is the one thing an expense book has to be able to hold before the cash
 * goes out. There is no unpaid income — money the school is owed is a fee invoice, which the fees
 * module already tracks, and inventing a second place for receivables would mean two answers to
 * "what are we owed".
 */
public enum EntryStatus {

	/** Settled. {@code moneyAccountId} and {@code paidOn} are set. */
	PAID,

	/** A bill payable, with a {@code dueDate} and no money account yet. */
	UNPAID
}

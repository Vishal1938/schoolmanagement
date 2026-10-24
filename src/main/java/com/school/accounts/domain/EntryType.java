package com.school.accounts.domain;

/**
 * Which way the money went.
 *
 * <p>A category has one of these and so does every entry, and for a manual entry the two have to
 * agree: "Electricity" is an expense category, so an income entry against it would be a typo that
 * quietly inverts a month's total.
 */
public enum EntryType {

	/** Money out: a bill, a purchase, a salary. */
	EXPENSE,

	/** Money in that is not a student's fees — a donation, a grant, scrap, interest. */
	INCOME
}

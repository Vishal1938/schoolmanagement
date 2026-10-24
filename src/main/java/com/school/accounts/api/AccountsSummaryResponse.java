package com.school.accounts.api;

import java.time.LocalDate;
import java.util.List;

import com.school.accounts.domain.MoneyAccountType;

/**
 * What the books add up to over a period. Every figure is in paise (CLAUDE.md rule 3).
 *
 * <p><strong>Advances are in neither {@code income} nor {@code expense}.</strong> Money handed to an
 * employee has left a drawer but is still the school's, and the installment that comes back is not
 * earnings; counting either would make a month with three advances look like a disaster. They are
 * reported on their own, with {@code advancesOutstanding} being what the staff owe.
 *
 * <p>{@code balances} are <strong>as of now</strong>, not as of the end of the range: "what is in
 * the drawer" has one answer, and it is not a function of which month is on screen.
 *
 * @param net                 {@code income} less {@code expense} over the range
 * @param payable             everything still unpaid, whatever the range. A bill falling due next
 *                            week does not belong to the month somebody happens to be reading
 * @param overdue             the part of {@code payable} whose due date has passed
 * @param byCategory          the range's totals per category, biggest first. Income and expense
 *                            categories together, since a category is already one or the other
 */
public record AccountsSummaryResponse(
		LocalDate from,
		LocalDate to,
		long income,
		long expense,
		long net,
		long advancesGiven,
		long advancesRecovered,
		long advancesOutstanding,
		long payable,
		long overdue,
		List<CategoryTotal> byCategory,
		List<AccountBalance> balances) {

	/** One category's total over the range, in paise. */
	public record CategoryTotal(String category, long amount) {
	}

	/** One money account and what is in it right now, in paise. */
	public record AccountBalance(
			String id,
			String name,
			MoneyAccountType type,
			boolean active,
			long balance) {
	}
}

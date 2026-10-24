package com.school.accounts.app;

import java.time.LocalDate;

import com.school.accounts.domain.EntrySource;
import com.school.accounts.domain.EntryStatus;
import com.school.accounts.domain.EntryType;

/**
 * The filters on {@code GET /accounts/entries}, bound from the query string.
 *
 * <p>Every one is optional and they combine with AND, so the day book, one vendor's history and
 * "what is still payable" are all the same endpoint. {@code from} and {@code to} are inclusive and
 * match on the entry's own {@code date} — the day the books record it on, not the day the row was
 * written.
 *
 * @param q an anchored prefix match against the party and the bill number
 */
public record EntrySearch(
		LocalDate from,
		LocalDate to,
		EntryType type,
		String category,
		String account,
		EntryStatus status,
		EntrySource source,
		String q) {
}

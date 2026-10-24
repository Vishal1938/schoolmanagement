package com.school.accounts.api;

import com.school.accounts.domain.AccountCategory;
import com.school.accounts.domain.EntryType;

/**
 * One category.
 *
 * @param system true for the three the application writes to itself. A client should show them —
 *               they are where the fee and salary figures are — and must not offer them on the
 *               entry form or let them be edited; the API refuses both anyway
 * @param asset  true for staff advances: the school's own money moving, so it is in neither the
 *               income nor the expense total
 */
public record AccountCategoryResponse(
		String id,
		String name,
		EntryType type,
		boolean system,
		boolean asset,
		boolean active) {

	public static AccountCategoryResponse of(AccountCategory category) {
		return new AccountCategoryResponse(
				category.getId(),
				category.getName(),
				category.getType(),
				category.isSystem(),
				category.isAsset(),
				category.isActive());
	}
}

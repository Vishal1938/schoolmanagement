package com.school.accounts.domain;

import java.time.Instant;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * What a line in the books is for: Electricity, Rent, Donation, Scrap sale.
 *
 * <p>Seeded with a list a school recognises and then entirely the institution's own business —
 * nothing here is hardcoded into the code paths (CLAUDE.md rule 6). Categories are referenced by id,
 * so renaming one does not have to be repeated across a year of entries.
 *
 * <p>Three of them are the application's own ({@link SystemCategory}): they carry a
 * {@link #systemKey}, cannot be edited or deleted, and are refused as the category of a manual
 * entry. {@link #asset} marks the one that is neither income nor expense, so reports can leave it
 * out of both totals.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(AccountCategory.COLLECTION)
// The category picker: one side of the books, active first, by name.
@CompoundIndex(name = "account_categories_type_active_name_idx", def = "{'type': 1, 'active': 1, 'name': 1}")
// A name is unique per side, so a school may have both an "Interest" income and an "Interest" expense.
@CompoundIndex(name = "account_categories_type_name_idx", def = "{'type': 1, 'name': 1}", unique = true)
public class AccountCategory {

	public static final String COLLECTION = "account_categories";

	@Id
	private String id;

	private String name;

	private EntryType type;

	/**
	 * Set on the three the application writes to itself, and null on everything a school adds. Unique
	 * and sparse, which is what makes the first-use seeding idempotent: a second attempt to seed
	 * "Fees collected" collides rather than creating a duplicate the automatic writer might then pick
	 * the wrong one of.
	 */
	@Indexed(name = "account_categories_system_key_idx", unique = true, sparse = true)
	private SystemCategory systemKey;

	/**
	 * True when entries in this category are the school's own money moving rather than income or
	 * expense — staff advances. Reports exclude it from both totals and show it on its own.
	 */
	private boolean asset;

	/** False retires the category from new entries without touching the ones that used it. */
	private boolean active;

	private Instant createdAt;

	private Instant updatedAt;

	/** Whether this is one of the application's own categories, which nobody may edit or pick. */
	public boolean isSystem() {
		return systemKey != null;
	}
}

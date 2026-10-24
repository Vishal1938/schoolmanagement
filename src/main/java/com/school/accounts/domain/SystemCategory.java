package com.school.accounts.domain;

/**
 * The three categories the application writes to itself.
 *
 * <p>They exist because fee collections, salaries and advances have to land somewhere, and because
 * they must not land in a category an administrator can rename, retire or point a manual entry at:
 * a month's income would then depend on whether somebody had tidied the category list. So they are
 * seeded with {@code systemKey} set, are not editable, and are refused as the category of a manual
 * entry — {@code POST /accounts/entries} answers 422 for one.
 *
 * <p>{@link #STAFF_ADVANCES} is the odd one. It holds money that has physically left a drawer and is
 * still the school's — an asset, not an expense — so it is {@link #asset()} and both directions are
 * booked against it: giving an advance on the expense side, recovering an installment on the income
 * side. Every report leaves asset categories out of its income and expense totals, which is what
 * stops an advance being counted as a cost and its recovery as earnings.
 */
public enum SystemCategory {

	/** Every captured fee payment, however it arrived. */
	FEES_COLLECTED("Fees collected", EntryType.INCOME, false),

	/** Net salaries actually paid out. */
	SALARIES("Salaries", EntryType.EXPENSE, false),

	/** Advances given and recovered. An asset; see the class comment. */
	STAFF_ADVANCES("Staff advances", EntryType.EXPENSE, true);

	private final String displayName;
	private final EntryType type;
	private final boolean asset;

	SystemCategory(String displayName, EntryType type, boolean asset) {
		this.displayName = displayName;
		this.type = type;
		this.asset = asset;
	}

	/** The name the category is seeded under, and what a report prints. */
	public String displayName() {
		return displayName;
	}

	/** The side the category sits on. {@link #STAFF_ADVANCES} also takes income entries. */
	public EntryType type() {
		return type;
	}

	/** Whether this is the school's own money moving rather than income or expense. */
	public boolean asset() {
		return asset;
	}
}

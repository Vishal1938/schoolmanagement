package com.school.fees.domain;

import java.util.Map;

/**
 * One head and what it costs in one installment of a structure.
 *
 * @param amount         paise, never a {@code double} (CLAUDE.md rule 3). What every section pays
 *                       unless {@link #sectionAmounts} says otherwise
 * @param sectionAmounts section name to the amount that section pays instead, in paise. Null or empty
 *                       — the normal case — means every section pays {@link #amount}. This is what
 *                       lets a college charge Year 1 and Year 2 of one course differently without
 *                       splitting them into two courses, and the keys are section names as stored on
 *                       the class, i.e. upper-case
 */
public record StructureItem(String headId, long amount, Map<String, Long> sectionAmounts) {

	/** Without section overrides, which is how most of the application builds one. */
	public StructureItem(String headId, long amount) {
		this(headId, amount, null);
	}

	/**
	 * What this head costs a student in the given section: the override if there is one for it,
	 * otherwise the plain amount.
	 */
	public long amountFor(String section) {
		if (section == null || sectionAmounts == null) {
			return amount;
		}
		Long override = sectionAmounts.get(section);
		return override == null ? amount : override;
	}
}

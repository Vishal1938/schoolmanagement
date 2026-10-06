package com.school.payroll.domain;

/**
 * One named deduction as the structure holds it: a rule, not an amount.
 *
 * <p>The amount is worked out per run by {@link DeductionType#amountOf(long, long)} and frozen into
 * the payroll record, so changing the PF rate next year does not rewrite last year's slips.
 *
 * @param value paise when {@code type} is FIXED, basis points when PERCENT_OF_BASIC
 */
public record DeductionRule(String name, DeductionType type, long value) {

	/** What this rule comes to against a given basic, in paise. */
	public long amountOf(long basic) {
		return type.amountOf(basic, value);
	}
}

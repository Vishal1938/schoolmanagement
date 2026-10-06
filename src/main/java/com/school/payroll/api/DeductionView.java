package com.school.payroll.api;

import com.school.payroll.domain.AppliedDeduction;
import com.school.payroll.domain.DeductionRule;
import com.school.payroll.domain.DeductionType;

/**
 * One deduction as the API returns it: the rule <em>and</em> what it comes to, so a client never has
 * to know that a percentage is held in basis points to show a figure.
 *
 * @param value  paise when {@code type} is FIXED, basis points when PERCENT_OF_BASIC (1200 is 12%)
 * @param amount what it comes to against the basic it was applied to, in paise
 */
public record DeductionView(String name, DeductionType type, long value, long amount) {

	/** A rule from a structure, costed against that structure's basic. */
	public static DeductionView of(DeductionRule rule, long basic) {
		return new DeductionView(rule.name(), rule.type(), rule.value(), rule.amountOf(basic));
	}

	/** A deduction from a payroll record, where the amount was settled at run time. */
	public static DeductionView of(AppliedDeduction deduction) {
		return new DeductionView(deduction.name(), deduction.type(), deduction.value(), deduction.amount());
	}
}

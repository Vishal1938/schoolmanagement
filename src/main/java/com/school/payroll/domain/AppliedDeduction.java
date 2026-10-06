package com.school.payroll.domain;

/**
 * One deduction as it was actually applied to one month: the rule, and what it came to.
 *
 * <p>The rule is copied in alongside the amount so a slip can print "PF (12% of basic)" and so a
 * figure stays explainable after the structure has moved on.
 *
 * @param value  the rule's value as it stood: paise for FIXED, basis points for PERCENT_OF_BASIC
 * @param amount what was withheld, in paise
 */
public record AppliedDeduction(String name, DeductionType type, long value, long amount) {
}

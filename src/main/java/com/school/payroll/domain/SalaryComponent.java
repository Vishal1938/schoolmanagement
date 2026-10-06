package com.school.payroll.domain;

/**
 * One named allowance, in paise. HRA, conveyance, whatever this school calls it.
 *
 * <p>Free text rather than an enum: allowances are a school's own invention, and CLAUDE.md rule 6
 * keeps anything school-specific out of the code.
 */
public record SalaryComponent(String name, long amount) {
}

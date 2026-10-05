package com.school.payroll.api;

import java.time.LocalDate;
import java.util.List;

import com.school.payroll.domain.DeductionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /payroll/structures/{employeeUniqueId}}: what this employee is to be paid from
 * {@code effectiveFrom} onwards.
 *
 * <p>A PUT that <strong>creates a new version</strong> rather than replacing one. Send the whole
 * structure every time — the allowances and deductions you leave out are gone from the new version,
 * which is how an allowance is removed. Earlier versions are untouched.
 *
 * @param basic         basic pay in paise, and the base for every PERCENT_OF_BASIC deduction
 * @param effectiveFrom the first day this version applies; may be in the future, and a run picks the
 *                      latest version effective by the end of its month
 */
public record SalaryStructureRequest(
		@Positive long basic,
		@Valid @Size(max = 20) List<Allowance> allowances,
		@Valid @Size(max = 20) List<Deduction> deductions,
		@NotNull LocalDate effectiveFrom) {

	/** @param amount in paise */
	public record Allowance(
			@NotBlank @Size(max = 60) String name,
			@PositiveOrZero long amount) {
	}

	/**
	 * @param value paise when {@code type} is FIXED, basis points when PERCENT_OF_BASIC — 1200 is
	 *              12%, and 75 is 0.75%. Percentages are not whole numbers in practice, and money
	 *              never goes through a {@code double} here
	 */
	public record Deduction(
			@NotBlank @Size(max = 60) String name,
			@NotNull DeductionType type,
			@PositiveOrZero long value) {
	}
}

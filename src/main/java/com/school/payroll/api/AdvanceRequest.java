package com.school.payroll.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /payroll/advances}: money handed over ahead of salary.
 *
 * @param amount          the whole advance, in paise
 * @param givenOn         when it was handed over; not in the future, and defaults to today
 * @param monthlyRecovery taken off each payroll record until it is cleared, in paise. Must not be
 *                        more than the advance itself, and the last installment is whatever is left
 */
public record AdvanceRequest(
		@NotBlank String employeeUniqueId,
		@Positive long amount,
		LocalDate givenOn,
		@NotNull @Positive Long monthlyRecovery,
		@Size(max = 200) String reason) {
}

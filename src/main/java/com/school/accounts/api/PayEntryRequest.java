package com.school.accounts.api;

import java.time.LocalDate;

import com.school.accounts.domain.EntryMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /accounts/entries/{id}/pay}: settling a bill payable.
 *
 * <p>There is no amount. A bill is paid in full or it is not paid — paying half of one is two bills,
 * which is how an office would record it anyway, and taking an amount here would mean inventing
 * partial-payment state for something the fees module already does properly for the other direction.
 *
 * @param paidOn the day the money went out. Defaults to today; a date in the future is refused
 */
public record PayEntryRequest(
		LocalDate paidOn,
		@NotBlank String moneyAccountId,
		@NotNull EntryMode mode,
		@Size(max = 80) String reference) {
}

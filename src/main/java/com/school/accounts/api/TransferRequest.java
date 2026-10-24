package com.school.accounts.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /accounts/transfers} — the week's cash banked, say.
 *
 * <p>Neither income nor expense: the same money is in a different place, so it appears in no month's
 * totals and only moves two balances.
 *
 * @param amount in paise, positive. The direction is the pair of accounts
 */
public record TransferRequest(
		@NotNull LocalDate date,
		@NotBlank String fromAccountId,
		@NotBlank String toAccountId,
		@Positive long amount,
		@Size(max = 80) String reference,
		@Size(max = 500) String note) {
}

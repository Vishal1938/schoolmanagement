package com.school.accounts.api;

import java.time.LocalDate;

import com.school.accounts.domain.MoneyAccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /accounts/money-accounts} and {@code PUT /accounts/money-accounts/{id}}.
 *
 * <p>{@code bankName} and {@code accountNumberLast4} apply to a BANK account and are
 * <em>ignored and cleared</em> on a CASH one rather than rejected, the same way the employee halves
 * are — so switching an account's type cannot leave a bank name on the cash drawer.
 *
 * @param openingBalance in paise, and nullable so that omitting it means zero rather than being a
 *                       validation error. May be negative: a bank account can be overdrawn
 * @param openingDate    the day the books start for this account. Defaults to today on a create and
 *                       is left as it was on an update
 * @param active         defaults to true on a create, and to whatever it was on an update
 */
public record MoneyAccountRequest(
		@NotBlank @Size(max = 120) String name,
		@NotNull MoneyAccountType type,
		@Size(max = 120) String bankName,
		@Pattern(regexp = "^[0-9]{4}$", message = "must be the last four digits of the account number")
		String accountNumberLast4,
		Long openingBalance,
		LocalDate openingDate,
		Boolean active) {
}

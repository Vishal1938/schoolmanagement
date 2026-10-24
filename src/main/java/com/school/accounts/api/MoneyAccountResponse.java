package com.school.accounts.api;

import java.time.Instant;
import java.time.LocalDate;

import com.school.accounts.domain.MoneyAccount;
import com.school.accounts.domain.MoneyAccountType;

/**
 * One money account, with what is in it.
 *
 * @param balance opening balance plus every settled entry and transfer since, in paise. Computed on
 *                read, never stored — see {@code MoneyAccountService}. Null on the response to a
 *                create or an update, where nothing has moved yet and the figure would be the
 *                opening balance dressed up as a balance
 */
public record MoneyAccountResponse(
		String id,
		String name,
		MoneyAccountType type,
		String bankName,
		String accountNumberLast4,
		long openingBalance,
		LocalDate openingDate,
		boolean active,
		Long balance,
		Instant createdAt,
		Instant updatedAt) {

	/** Without a balance, for a create or an update. */
	public static MoneyAccountResponse of(MoneyAccount account) {
		return of(account, null);
	}

	public static MoneyAccountResponse of(MoneyAccount account, Long balance) {
		return new MoneyAccountResponse(
				account.getId(),
				account.getName(),
				account.getType(),
				account.getBankName(),
				account.getAccountNumberLast4(),
				account.getOpeningBalance(),
				account.getOpeningDate(),
				account.isActive(),
				balance,
				account.getCreatedAt(),
				account.getUpdatedAt());
	}
}

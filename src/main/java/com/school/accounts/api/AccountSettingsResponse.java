package com.school.accounts.api;

import java.time.Instant;
import java.time.LocalDate;

import com.school.accounts.domain.AccountDefaults;
import com.school.accounts.domain.AccountSettings;

/**
 * The expense book's settings.
 *
 * @param defaults   the money accounts the automatic entries land in. Never null, though any field
 *                   inside it may be
 * @param lockedUpTo the last closed day, or null when nothing is closed. A client should grey out
 *                   dates on or before it rather than letting a save 422
 */
public record AccountSettingsResponse(
		AccountDefaults defaults,
		LocalDate lockedUpTo,
		Instant updatedAt) {

	public static AccountSettingsResponse of(AccountSettings settings) {
		return new AccountSettingsResponse(settings.resolvedDefaults(), settings.getLockedUpTo(),
				settings.getUpdatedAt());
	}
}

package com.school.accounts.domain;

import java.time.Instant;
import java.time.LocalDate;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The one settings document of the expense book: where the automatic entries go, and how far back
 * the books are closed.
 *
 * <p>Exactly one of these, enforced by the fixed {@link #SINGLETON_ID} rather than by a query — the
 * same shape as {@code school_config}, so two instances booting against an empty database cannot
 * both seed it.
 *
 * <p>It is here rather than in {@code school_config} because it refers to {@code money_accounts}
 * ids, which belong to this module: putting them in the configuration document would make the
 * school configuration depend on the accounts collections, and a module boundary exists precisely to
 * stop that.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(AccountSettings.COLLECTION)
public class AccountSettings {

	public static final String COLLECTION = "account_settings";

	/** Fixed id of the single document; see the class comment. */
	public static final String SINGLETON_ID = "account-settings";

	@Id
	private String id;

	/** Guards two administrators overwriting each other through {@code PUT /accounts/settings}. */
	@Version
	private Long version;

	/** Null on a document written before anything was configured; reads as {@link AccountDefaults#NONE}. */
	private AccountDefaults defaults;

	/**
	 * The books are closed on and before this date: no entry or transfer dated then can be added,
	 * edited or deleted. Null means nothing is closed.
	 *
	 * <p>Deliberately a date and not a month. "Closed to 30 September" is what an office actually
	 * says, and a month boundary would make closing a half-month impossible.
	 */
	private LocalDate lockedUpTo;

	private Instant createdAt;

	private Instant updatedAt;

	/** The defaults, never null. */
	public AccountDefaults resolvedDefaults() {
		return defaults == null ? AccountDefaults.NONE : defaults;
	}
}

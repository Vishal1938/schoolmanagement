package com.school.accounts.domain;

import java.time.Instant;
import java.time.LocalDate;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Somewhere the school keeps money: the cash drawer, or a bank account.
 *
 * <p>Every entry and both ends of every transfer name one, which is what makes "how much is in the
 * drawer" answerable. The balance is <strong>not stored</strong>: it is the opening balance plus
 * everything booked to the account since, computed on read. A stored running total would be one
 * number that has to be right after every write, including the automatic ones, and the first
 * mismatch would be invisible.
 *
 * <p>{@code openingBalance} is in paise, like all money here (CLAUDE.md rule 3), and may be
 * negative for an overdrawn account.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(MoneyAccount.COLLECTION)
public class MoneyAccount {

	public static final String COLLECTION = "money_accounts";

	@Id
	private String id;

	/** Unique: two accounts with one name are indistinguishable on the entry screen. */
	@Indexed(name = "money_accounts_name_idx", unique = true)
	private String name;

	private MoneyAccountType type;

	/** Null on a CASH account. */
	private String bankName;

	/**
	 * Last four digits of the account number, and never more. The books only ever need to say "ending
	 * 4821"; the full number lives in the bank's records and not storing it is cheaper than protecting
	 * it — the same choice {@code Employee.panLast4} makes.
	 */
	private String accountNumberLast4;

	/** What was in it on {@link #openingDate}, in paise. Negative for an overdraft. */
	private long openingBalance;

	/** The day the books start for this account. Entries before it are allowed and simply precede it. */
	private LocalDate openingDate;

	/** False keeps the account off the entry screen while leaving its history readable. */
	private boolean active;

	private Instant createdAt;

	private Instant updatedAt;
}

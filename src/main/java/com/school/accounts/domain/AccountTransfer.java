package com.school.accounts.domain;

import java.time.Instant;
import java.time.LocalDate;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Money moved between two of the school's own accounts — the week's cash takings banked, say.
 *
 * <p><strong>Neither income nor expense.</strong> Nothing was earned and nothing was spent: the same
 * money is in a different place. So it is kept here rather than as a pair of entries, which would
 * otherwise have to be excluded by every report, and the two money account balances read this
 * collection alongside {@link AccountEntry}.
 *
 * <p>Append-only, like a payment. A transfer entered wrongly is corrected by a transfer the other
 * way, so that both statements show what actually happened.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(AccountTransfer.COLLECTION)
// The transfer list, newest first.
@CompoundIndex(name = "account_transfers_date_idx", def = "{'date': -1}")
// The two balance queries: everything out of an account, and everything into one.
@CompoundIndex(name = "account_transfers_from_idx", def = "{'fromAccountId': 1, 'date': -1}")
@CompoundIndex(name = "account_transfers_to_idx", def = "{'toAccountId': 1, 'date': -1}")
public class AccountTransfer {

	public static final String COLLECTION = "account_transfers";

	@Id
	private String id;

	/** The day the money moved. What the month lock is applied against. */
	private LocalDate date;

	private String fromAccountId;

	private String toAccountId;

	/** In paise, always positive; the direction is the pair of accounts. */
	private long amount;

	/** The deposit slip number, the UTR — whatever traces it. */
	private String reference;

	private String note;

	/** {@code uniqueId} of whoever recorded it. */
	private String recordedBy;

	private Instant createdAt;
}

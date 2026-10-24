package com.school.accounts.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One line in the books: an expense, or income that is not a student's fees.
 *
 * <p>One collection for what the office types in and what the application writes for itself, with
 * {@link #source} telling them apart. That is deliberate: the electricity bill and the month's
 * salaries are the same kind of fact and belong in the same total, and a second collection would
 * mean every report adding two lists together and every filter being written twice.
 *
 * <p><strong>Money is {@code long} paise</strong> (CLAUDE.md rule 3), and {@link #amount} is always
 * positive — the direction is {@link #type}, never a sign, so no report has to decide whether a
 * negative expense is a refund or a typo.
 *
 * <p>{@link #status} is {@code UNPAID} only for an expense, which is a bill the school has received
 * and not yet settled. It has a {@link #dueDate} and no money account until
 * {@code POST /accounts/entries/{id}/pay} settles it.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(AccountEntry.COLLECTION)
// The day book and every date-ranged report: newest first within a range.
@CompoundIndex(name = "account_entries_date_idx", def = "{'date': -1}")
// The two filters that narrow a range — one side of the books, or one category.
@CompoundIndex(name = "account_entries_type_date_idx", def = "{'type': 1, 'date': -1}")
@CompoundIndex(name = "account_entries_category_date_idx", def = "{'categoryId': 1, 'date': -1}")
// An account's statement, and what its balance is computed from.
@CompoundIndex(name = "account_entries_money_account_date_idx", def = "{'moneyAccountId': 1, 'date': -1}")
// Bills payable, soonest due first.
@CompoundIndex(name = "account_entries_status_due_idx", def = "{'status': 1, 'dueDate': 1}")
public class AccountEntry {

	public static final String COLLECTION = "account_entries";

	@Id
	private String id;

	private EntryType type;

	/**
	 * The day the books record this on — the bill's date, or the day the money moved. Not when the row
	 * was written: an invoice entered a week late still belongs to the week it is dated, which is what
	 * every report and the month lock work from.
	 */
	private LocalDate date;

	private String categoryId;

	/** In paise, always positive. The direction is {@link #type}. */
	private long amount;

	/** Who was paid, or who paid — a vendor, a donor. Free text; most of them are not in any register. */
	private String party;

	private String description;

	/** The account it came out of or went into. Null while an expense is still {@code UNPAID}. */
	private String moneyAccountId;

	/** Null while an expense is still {@code UNPAID}. */
	private EntryMode mode;

	/** Cheque number, UTR or UPI reference. Null for cash. */
	private String reference;

	/** The vendor's bill or invoice number, as printed on the paper. */
	private String billNo;

	private List<EntryAttachment> attachments;

	private EntryStatus status;

	/** When a bill payable falls due. Null on a PAID entry. */
	private LocalDate dueDate;

	/** The day it was settled. Null while UNPAID; equal to {@link #date} for an entry paid at once. */
	private LocalDate paidOn;

	/** {@code uniqueId} of whoever entered it. Null for an entry the application wrote itself. */
	private String recordedBy;

	private EntrySource source;

	/**
	 * What this entry is the shadow of, for everything but a manual one — e.g. {@code payment:67a1…},
	 * {@code payroll:67b4…}, {@code advance:67c9…}.
	 *
	 * <p><strong>This index is the idempotency guard.</strong> The automatic writers are driven by
	 * events that can be delivered more than once, and by a backfill that may be run twice; a unique
	 * key means the second attempt collides rather than booking the same salary twice. Sparse, because
	 * every manual entry leaves it null and a unique index over many nulls would reject the second.
	 */
	@Indexed(name = "account_entries_source_ref_idx", unique = true, sparse = true)
	private String sourceRef;

	private Instant createdAt;

	private Instant updatedAt;

	/** Whether the office may still edit or delete this line. Only what somebody typed in. */
	public boolean isManual() {
		return source == null || source == EntrySource.MANUAL;
	}

	/** The signed effect on a money account's balance, in paise: income adds, expense takes away. */
	public long signedAmount() {
		return type == EntryType.INCOME ? amount : -amount;
	}
}

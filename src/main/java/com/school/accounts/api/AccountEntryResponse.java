package com.school.accounts.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.school.accounts.domain.AccountCategory;
import com.school.accounts.domain.AccountEntry;
import com.school.accounts.domain.EntryAttachment;
import com.school.accounts.domain.EntryMode;
import com.school.accounts.domain.EntrySource;
import com.school.accounts.domain.EntryStatus;
import com.school.accounts.domain.EntryType;

/**
 * One line of the day book.
 *
 * <p>The category and money account names are resolved by the caller and passed in, so a page of
 * fifty entries costs two extra reads rather than a hundred. A name that comes back null means the
 * document it pointed at has gone, which is only reachable for a category or account deleted while
 * nothing referred to it.
 *
 * @param editable whether {@code PUT} and {@code DELETE} will accept this entry — false for
 *                 everything the application wrote itself. A client should render those read-only
 *                 and link to fees or payroll instead of offering an edit that will 422
 */
public record AccountEntryResponse(
		String id,
		EntryType type,
		LocalDate date,
		String categoryId,
		String categoryName,
		boolean asset,
		long amount,
		String party,
		String description,
		String moneyAccountId,
		String moneyAccountName,
		EntryMode mode,
		String reference,
		String billNo,
		List<EntryAttachment> attachments,
		EntryStatus status,
		LocalDate dueDate,
		LocalDate paidOn,
		String recordedBy,
		EntrySource source,
		String sourceRef,
		boolean editable,
		Instant createdAt,
		Instant updatedAt) {

	public static AccountEntryResponse of(AccountEntry entry, Map<String, AccountCategory> categories,
			Map<String, String> accountNames) {
		AccountCategory category = entry.getCategoryId() == null ? null : categories.get(entry.getCategoryId());
		return new AccountEntryResponse(
				entry.getId(),
				entry.getType(),
				entry.getDate(),
				entry.getCategoryId(),
				category == null ? null : category.getName(),
				category != null && category.isAsset(),
				entry.getAmount(),
				entry.getParty(),
				entry.getDescription(),
				entry.getMoneyAccountId(),
				entry.getMoneyAccountId() == null ? null : accountNames.get(entry.getMoneyAccountId()),
				entry.getMode(),
				entry.getReference(),
				entry.getBillNo(),
				entry.getAttachments() == null ? List.of() : entry.getAttachments(),
				entry.getStatus(),
				entry.getDueDate(),
				entry.getPaidOn(),
				entry.getRecordedBy(),
				entry.getSource(),
				entry.getSourceRef(),
				entry.isManual(),
				entry.getCreatedAt(),
				entry.getUpdatedAt());
	}
}

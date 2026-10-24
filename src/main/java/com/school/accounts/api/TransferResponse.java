package com.school.accounts.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import com.school.accounts.domain.AccountTransfer;

/** One transfer, with both account names resolved by the caller in one read. */
public record TransferResponse(
		String id,
		LocalDate date,
		String fromAccountId,
		String fromAccountName,
		String toAccountId,
		String toAccountName,
		long amount,
		String reference,
		String note,
		String recordedBy,
		Instant createdAt) {

	public static TransferResponse of(AccountTransfer transfer, Map<String, String> accountNames) {
		return new TransferResponse(
				transfer.getId(),
				transfer.getDate(),
				transfer.getFromAccountId(),
				accountNames.get(transfer.getFromAccountId()),
				transfer.getToAccountId(),
				accountNames.get(transfer.getToAccountId()),
				transfer.getAmount(),
				transfer.getReference(),
				transfer.getNote(),
				transfer.getRecordedBy(),
				transfer.getCreatedAt());
	}
}

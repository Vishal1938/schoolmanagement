package com.school.accounts.api;

import java.time.LocalDate;
import java.util.List;

import com.school.accounts.domain.EntryAttachment;
import com.school.accounts.domain.EntryMode;
import com.school.accounts.domain.EntryStatus;
import com.school.accounts.domain.EntryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /accounts/entries} and {@code PUT /accounts/entries/{id}}.
 *
 * <p>Which fields matter depends on {@code status}, and those rules are in
 * {@code AccountEntryService} rather than here because each of them reads a second field, which Bean
 * Validation cannot do:
 *
 * <ul>
 * <li>{@code PAID} (the default) needs {@code moneyAccountId} and {@code mode}. {@code paidOn}
 * defaults to {@code date}.</li>
 * <li>{@code UNPAID} needs {@code dueDate}, is <strong>only valid for an EXPENSE</strong>, and
 * ignores the money account, mode and reference — the bill has not been paid, so there is nothing
 * to say about how.</li>
 * </ul>
 *
 * <p>{@code amount} is in paise and must be positive: the direction is {@code type}, never a sign.
 *
 * <p>{@code attachments} must be objects returned by {@code POST /accounts/entries/attachments};
 * a URL from anywhere else is refused, so the books cannot become a redirect service.
 *
 * @param categoryId must not be one of the application's own categories — a fee collection is
 *                   recorded as a payment and a salary through payroll, and a hand-written line in
 *                   either would double-count the month
 */
public record AccountEntryRequest(
		@NotNull EntryType type,
		@NotNull LocalDate date,
		@NotBlank String categoryId,
		@Positive long amount,
		@Size(max = 160) String party,
		@Size(max = 500) String description,
		String moneyAccountId,
		EntryMode mode,
		@Size(max = 80) String reference,
		@Size(max = 80) String billNo,
		@Size(max = 5) List<EntryAttachment> attachments,
		EntryStatus status,
		LocalDate dueDate,
		LocalDate paidOn) {

	/** Omitted means the money has already moved, which is what almost every entry is. */
	public EntryStatus statusOrDefault() {
		return status == null ? EntryStatus.PAID : status;
	}
}

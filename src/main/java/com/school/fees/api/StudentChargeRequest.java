package com.school.fees.api;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /fees/charges}: a charge for named students rather than for a class.
 *
 * <p>"Late library fine", "replacement ID card", "bus fare for the Delhi trip". It raises one
 * {@code CUSTOM} invoice per student, each a bill in its own right, and each listed on that student's
 * ledger alongside their monthly fees with no special handling.
 *
 * <p>One request, many students, one amount: the whole point is charging a list of people the same
 * thing in one go. Two students owing different amounts are two requests.
 *
 * @param studentUniqueIds who to charge. Every id must be a real student, or nothing is written
 * @param name             what the charge is for; it heads the line on the ledger and the receipt
 * @param headId           the fee head it is charged under, so it lands in the right column of the
 *                         collection report. Must exist and still be active
 * @param amount           paise, the same for every student named
 * @param dueDate          when it falls due. A CUSTOM charge carries no late fine whatever happens —
 *                         it has no structure behind it and therefore no rule — so this date is for
 *                         the register and the reminder, not for a penalty
 * @param note             optional detail for the office: which book, which trip
 */
public record StudentChargeRequest(
		@NotEmpty @Size(max = 200) List<@NotBlank String> studentUniqueIds,
		@NotBlank @Size(max = 40) String name,
		@NotBlank String headId,
		@PositiveOrZero long amount,
		@NotNull LocalDate dueDate,
		@Size(max = 500) String note) {
}

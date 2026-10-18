package com.school.fees.api;

import java.time.YearMonth;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /fees/structures/monthly}: a year of monthly fees in one call.
 *
 * <p>This is a convenience over {@code POST /fees/structures}, not a second model. It writes ordinary
 * {@code MONTHLY} installments — one per month, named {@code "Jul 2026"} — into the structure for that
 * session and class, creating the structure if there is none. Everything it writes can also be written
 * by hand through the full endpoint; nobody wants to type twelve installments.
 *
 * <p>Run it again to change the figure: months that have no invoices yet are rewritten, months that
 * already have invoices are left exactly as they are and reported as skipped. An invoice is a
 * statement of what a family was charged, and this endpoint is not the place to restate one.
 *
 * @param sessionId      optional; defaults to the active session
 * @param classId        the class, or the college's course
 * @param headId         the fee head the monthly amount is charged under — "Tuition fee". Other heads
 *                       already on those months are left alone
 * @param monthlyAmount  paise per month, for every section without an override
 * @param sectionAmounts optional, section name to the amount that section pays instead. This is how
 *                       Year 1 and Year 2 of one course charge differently
 * @param startMonth     the first month to bill, as {@code "2026-07"}
 * @param months         how many months from {@code startMonth}, defaulting to 12
 * @param dueDay         day of the month each installment falls due, defaulting to the 10th. Clamped
 *                       to the length of the month, so 31 is the 28th in February
 */
public record MonthlyFeeSetupRequest(
		String sessionId,
		@NotBlank String classId,
		@NotBlank String headId,
		@PositiveOrZero long monthlyAmount,
		@Size(max = 40) Map<@NotBlank String, @NotNull @PositiveOrZero Long> sectionAmounts,
		@NotNull YearMonth startMonth,
		@Min(1) @Max(24) Integer months,
		@Min(1) @Max(31) Integer dueDay) {

	/** 12 months unless the caller said otherwise — an academic year of monthly fees. */
	public int monthsOrDefault() {
		return months == null ? 12 : months;
	}

	/** The 10th unless the caller said otherwise. */
	public int dueDayOrDefault() {
		return dueDay == null ? 10 : dueDay;
	}
}

package com.school.fees.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /fees/structures/semester}: a session's semesters in one call.
 *
 * <p>The semester counterpart of {@link MonthlyFeeSetupRequest}, and the same kind of thing — a
 * convenience over {@code POST /fees/structures} that writes {@code SEMESTER} installments into the
 * structure for that session and course, creating the structure if there is none, and extending it
 * rather than replacing it. The three outcomes per semester are the monthly ones: added, rewritten
 * while it has no invoices, or left exactly as it is once it has.
 *
 * <p><strong>A semester carries its period explicitly</strong>, which is the one way it differs from a
 * month. "Jul 2026" covers July because of its name; "Semester 3" due on 31 July covers July to
 * December and nothing about the due date says so, so the dates are the client's to state.
 *
 * @param sessionId      optional; defaults to the active session
 * @param classId        the course — "B.Com Year 2"
 * @param headId         the fee head the semester amount is charged under. Other heads already on
 *                       those semesters are left alone
 * @param amount         paise per semester, for every section without an override
 * @param sectionAmounts optional, section name to the amount that section pays instead. This is how
 *                       two years of one course charge differently
 * @param semesters      the semesters to write, at least one. Numbers must be unique and periods must
 *                       not overlap, within the request or against the semesters already on the
 *                       structure
 */
public record SemesterFeeSetupRequest(
		String sessionId,
		@NotBlank String classId,
		@NotBlank String headId,
		@PositiveOrZero long amount,
		@Size(max = 40) Map<@NotBlank String, @NotNull @PositiveOrZero Long> sectionAmounts,
		@Valid @NotEmpty @Size(max = 12) List<Semester> semesters) {

	/**
	 * One semester of the course.
	 *
	 * @param number      which semester of the whole programme this is — 3 and 4 for the second year of
	 *                    a three-year degree. It is what the installment is named after, so it is an
	 *                    identifier: {@code "Semester 3"} is the name invoices refer back to
	 * @param label       optional. Omit it and the label is derived from the number and the period —
	 *                    {@code "Semester 3 (Jul-Dec 2026)"} — which is what a register column and a
	 *                    receipt line should read
	 * @param periodStart first day the semester covers
	 * @param periodEnd   last day it covers, inclusive. Must not be before {@code periodStart}
	 * @param dueDate     when the semester's fees fall due. The late fine runs from here exactly as it
	 *                    does for a month
	 */
	public record Semester(
			@Min(1) @Max(16) int number,
			@Size(max = 60) String label,
			@NotNull LocalDate periodStart,
			@NotNull LocalDate periodEnd,
			@NotNull LocalDate dueDate) {
	}
}

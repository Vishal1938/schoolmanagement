package com.school.fees.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.school.fees.domain.InstallmentKind;
import com.school.fees.domain.LateFineType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /fees/structures} and {@code PUT /fees/structures/{id}}.
 *
 * <p>{@code sessionId} is absent: a structure always belongs to the active session, which is not the
 * client's to choose — the same stance as exams. On a {@code PUT} the {@code classId} must match the
 * structure being edited; a structure cannot be moved to another class, because the invoices already
 * generated from it name the old one.
 *
 * <p>This endpoint still writes the whole structure at once. A monthly plan is far easier to set up
 * through {@code POST /fees/structures/monthly}, and a one-off course-wide charge through
 * {@code POST /fees/structures/{id}/one-time-fees}, both of which add to a structure rather than
 * replacing it — but anything either of those writes can also be written here, which is what keeps
 * them conveniences rather than a second model.
 *
 * @param lateFine null when the school charges nothing for paying late
 */
public record FeeStructureRequest(
		@NotBlank String classId,
		@Valid @NotEmpty @Size(max = 40) List<Installment> installments,
		@Valid LateFine lateFine) {

	/**
	 * Amounts are paise. Head ids must exist and must still be active.
	 *
	 * @param kind  omit for {@link InstallmentKind#MONTHLY}, which is what every installment written
	 *              before this field existed is. {@code CUSTOM} is rejected: a charge for named
	 *              students is not part of a structure — that is {@code POST /fees/charges}
	 * @param label how to head the register column, when it differs from the name. Omit to use the name
	 */
	public record Installment(
			@NotBlank @Size(max = 40) String name,
			@NotNull LocalDate dueDate,
			InstallmentKind kind,
			@Size(max = 60) String label,
			@Valid @NotEmpty @Size(max = 40) List<Item> items) {
	}

	/**
	 * @param sectionAmounts optional, section name to the amount that section pays instead of
	 *                       {@code amount}, in paise. Every key must be a section the class actually
	 *                       has. This is how Year 1 and Year 2 of one course charge differently
	 */
	public record Item(
			@NotBlank String headId,
			@PositiveOrZero long amount,
			@Size(max = 40) Map<@NotBlank String, @NotNull @PositiveOrZero Long> sectionAmounts) {
	}

	/** {@code cap} of 0 means uncapped; for PER_DAY that is worth thinking about before saving. */
	public record LateFine(
			@NotNull LateFineType type,
			@PositiveOrZero long amount,
			@PositiveOrZero int graceDays,
			@PositiveOrZero long cap) {
	}
}

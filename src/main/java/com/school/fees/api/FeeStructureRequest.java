package com.school.fees.api;

import java.time.LocalDate;
import java.util.List;

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
 * @param lateFine null when the school charges nothing for paying late
 */
public record FeeStructureRequest(
		@NotBlank String classId,
		@Valid @NotEmpty @Size(max = 24) List<Installment> installments,
		@Valid LateFine lateFine) {

	/** Amounts are paise. Head ids must exist and must still be active. */
	public record Installment(
			@NotBlank @Size(max = 40) String name,
			@NotNull LocalDate dueDate,
			@Valid @NotEmpty @Size(max = 40) List<Item> items) {
	}

	public record Item(
			@NotBlank String headId,
			@PositiveOrZero long amount) {
	}

	/** {@code cap} of 0 means uncapped; for PER_DAY that is worth thinking about before saving. */
	public record LateFine(
			@NotNull LateFineType type,
			@PositiveOrZero long amount,
			@PositiveOrZero int graceDays,
			@PositiveOrZero long cap) {
	}
}

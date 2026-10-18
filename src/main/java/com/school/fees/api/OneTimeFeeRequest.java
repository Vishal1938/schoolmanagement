package com.school.fees.api;

import java.time.LocalDate;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST} and {@code PUT /fees/structures/{id}/one-time-fees}: a charge the whole class
 * pays once — "Admission fee", "Laboratory deposit".
 *
 * <p>It becomes a {@code ONE_TIME} installment of the structure, which means the ordinary
 * {@code POST /fees/structures/{id}/generate-invoices} raises it for every student on the roll, and
 * raises <em>only</em> it on a second run because the months already have their invoices.
 *
 * <p>On a {@code PUT} the name in the path identifies the fee; {@code name} in the body may rename it,
 * but only while no invoice has been raised from it — the name is part of the idempotency key, so
 * renaming one that has been billed would raise the whole thing again under the new name.
 *
 * @param name           what it is called, and the installment name it is stored under
 * @param headId         the fee head it is charged under; must exist and still be active
 * @param amount         paise, for every section without an override
 * @param sectionAmounts optional, section name to the amount that section pays instead
 * @param dueDate        when it falls due; the late-fine rule of the structure applies to it as to
 *                       any other installment
 */
public record OneTimeFeeRequest(
		@NotBlank @Size(max = 40) String name,
		@NotBlank String headId,
		@PositiveOrZero long amount,
		@Size(max = 40) Map<@NotBlank String, @NotNull @PositiveOrZero Long> sectionAmounts,
		@NotNull LocalDate dueDate) {
}

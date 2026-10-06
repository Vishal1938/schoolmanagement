package com.school.fees.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /fees/heads} and {@code PUT /fees/heads/{id}}.
 *
 * @param active null on a create means {@code true} — a head is being added because the school is
 *               about to charge it
 */
public record FeeHeadRequest(
		@NotBlank @Size(max = 60) String name,
		Boolean active) {
}

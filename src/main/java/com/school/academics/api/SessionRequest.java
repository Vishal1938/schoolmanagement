package com.school.academics.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /sessions}.
 *
 * <p>There is no {@code active} flag on purpose: {@code PUT /sessions/{id}/activate} is the only way
 * to switch the current session, so a create cannot move the whole school by accident. The very first
 * session of a deployment is the one exception and is activated automatically.
 *
 * <p>{@code name} is free text rather than a pattern — "2026-27", "2026-2027" and "AY 2026/27" are all
 * in use, and nothing school-specific is hardcoded here.
 */
public record SessionRequest(
		@NotBlank @Size(max = 32) String name,
		@NotNull LocalDate startDate,
		@NotNull LocalDate endDate) {
}

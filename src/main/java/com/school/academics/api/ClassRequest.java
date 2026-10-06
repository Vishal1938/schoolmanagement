package com.school.academics.api;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /classes} and {@code PUT /classes/{id}}.
 *
 * <p>Assignments are not in here. They are set through {@code PUT /classes/{id}/assignments} so that
 * editing a class name cannot silently wipe who teaches it. An update that drops a section or a
 * subject does drop the assignments that referred to it — there is nothing else they could mean.
 */
public record ClassRequest(
		@NotBlank @Size(max = 60) String name,
		@Min(0) @Max(1000) int order,
		@NotEmpty @Size(max = 26) List<@NotBlank @Size(max = 8) String> sections,
		@Size(max = 40) List<@NotBlank String> subjectIds) {
}

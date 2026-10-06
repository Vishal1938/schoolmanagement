package com.school.academics.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /subjects} and {@code PUT /subjects/{id}}. The code is upper-cased by the
 * service, so {@code eng} and {@code ENG} are the same subject and the second one is rejected.
 */
public record SubjectRequest(
		@NotBlank @Size(max = 80) String name,
		@NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{2,16}$",
				message = "must be 2-16 letters, digits or hyphens") String code) {
}

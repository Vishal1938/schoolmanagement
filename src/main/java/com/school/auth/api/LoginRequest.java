package com.school.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Login credentials. People log in with their {@code uniqueId}, not an email address: it is the one
 * identifier every account has, and it is what appears on registers, receipts and report cards.
 */
public record LoginRequest(
		@NotBlank @Schema(example = "DEMO-EMP-26-0001") String uniqueId,
		@NotBlank @Schema(description = "Plain password. Never logged, never echoed back.") String password) {
}

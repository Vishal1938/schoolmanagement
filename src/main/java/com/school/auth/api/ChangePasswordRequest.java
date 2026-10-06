package com.school.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A password change by the account holder. Only length is required of the new password: composition rules
 * push people towards predictable substitutions, while length is what actually costs an attacker.
 *
 * <p>An admin resetting someone else's password is a different operation and does not go through here.
 */
public record ChangePasswordRequest(
		@NotBlank String currentPassword,
		@NotBlank
		@Size(min = 8, max = 128, message = "must be between 8 and 128 characters")
		@Schema(minLength = 8, description = "Must differ from the current password.") String newPassword) {
}

package com.school.auth.api;

import java.util.List;

import com.school.common.security.AuthPrincipal;
import com.school.common.security.Role;

/**
 * The {@code user} object from API_CONTRACT.md §2, returned by both {@code /auth/login} and
 * {@code /auth/me}.
 *
 * <p>The frontend decides what to show from {@code permissions}, never from {@code role}.
 *
 * @param profilePhotoUrl always null for now: the photo lives on the student or employee profile, which
 *                        B5 and B6 add
 */
public record AuthUserResponse(
		String uniqueId,
		String name,
		Role role,
		List<String> permissions,
		boolean mustChangePassword,
		String profilePhotoUrl) {

	public static AuthUserResponse from(AuthPrincipal principal) {
		return new AuthUserResponse(
				principal.uniqueId(),
				principal.name(),
				principal.role(),
				principal.permissionNames(),
				principal.mustChangePassword(),
				null);
	}
}

package com.school.common.security;

import java.util.Optional;

import com.school.common.exceptions.AppException;
import com.school.common.exceptions.ErrorType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the authenticated caller out of the security context.
 *
 * <p>Services use {@link #require()} for object-level checks — "a student may only read their own
 * records" — instead of taking a uniqueId from the request, which the client controls.
 */
public final class CurrentUser {

	public static Optional<AuthPrincipal> principal() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return Optional.empty();
		}
		return authentication.getPrincipal() instanceof AuthPrincipal principal
				? Optional.of(principal)
				: Optional.empty();
	}

	/**
	 * @throws AppException 401 when there is no authenticated caller. Reaching this from an endpoint
	 *                      that the filter chain protects means a configuration mistake, not a client one
	 */
	public static AuthPrincipal require() {
		return principal().orElseThrow(
				() -> new AppException(ErrorType.UNAUTHORIZED, "Authentication is required for this endpoint"));
	}

	private CurrentUser() {
	}
}

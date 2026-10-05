package com.school.common.security;

import java.io.IOException;
import java.util.Set;

import com.school.common.exceptions.ErrorType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Blocks an account that is still on an issued password. While {@code mustChangePassword} is set, every
 * endpoint but the three needed to get out of that state answers 403 with
 * {@code PASSWORD_CHANGE_REQUIRED}, as API_CONTRACT.md §2 requires.
 *
 * <p>Enforced here rather than in each controller: a new endpoint is protected by existing, and a student
 * who never changed the password an admin handed out cannot read marks, sit a quiz or pay a fee with it.
 *
 * <p>The flag is read from the token claim, so a caller who has just changed their password must use the
 * access token that {@code /auth/change-password} returned; the one they changed it with still says the
 * change is pending. Not a Spring bean, for the same reason as {@link JwtAuthenticationFilter}.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

	private final Set<String> allowedPaths;
	private final ProblemDetailResponseWriter writer;

	public PasswordChangeRequiredFilter(String apiBasePath, ProblemDetailResponseWriter writer) {
		// /auth/refresh needs no entry: it authenticates by cookie, with no access token and therefore no
		// principal for this filter to inspect, so it passes through regardless. That is harmless — the
		// access token it hands back still carries the flag, so the holder is still confined to these three.
		this.allowedPaths = Set.of(
				apiBasePath + "/auth/me",
				apiBasePath + "/auth/change-password",
				apiBasePath + "/auth/logout");
		this.writer = writer;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		boolean blocked = CurrentUser.principal()
				.map(AuthPrincipal::mustChangePassword)
				.orElse(false);
		if (blocked && !allowedPaths.contains(request.getRequestURI())) {
			writer.write(request, response, ErrorType.PASSWORD_CHANGE_REQUIRED,
					"Set a new password before using the rest of the application");
			return;
		}
		chain.doFilter(request, response);
	}
}

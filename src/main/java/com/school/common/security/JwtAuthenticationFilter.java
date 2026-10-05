package com.school.common.security;

import java.io.IOException;

import com.school.common.exceptions.AppException;
import com.school.common.jwt.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns an {@code Authorization: Bearer <jwt>} header into an authenticated
 * {@link AuthPrincipal} in the security context.
 *
 * <p>A request with no header passes straight through unauthenticated: whether that is allowed is the
 * filter chain's decision, so the public endpoints keep working and everything else ends at the 401
 * entry point. A header that is present but bad is a different matter — it is answered here and now with
 * {@code TOKEN_EXPIRED} or {@code TOKEN_INVALID}, because a client that sent a stale token needs to know
 * to refresh rather than be told it sent no credentials at all.
 *
 * <p>Not a Spring bean on purpose: {@code SecurityConfig} constructs it. A {@code Filter} bean would also
 * be registered in the plain servlet chain by Boot and would then run twice per request.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtService jwtService;
	private final ProblemDetailResponseWriter writer;

	public JwtAuthenticationFilter(JwtService jwtService, ProblemDetailResponseWriter writer) {
		this.jwtService = jwtService;
		this.writer = writer;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String token = bearerToken(request);
		if (token == null) {
			chain.doFilter(request, response);
			return;
		}
		try {
			AuthPrincipal principal = jwtService.parse(token);
			UsernamePasswordAuthenticationToken authentication =
					new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}
		catch (AppException ex) {
			// The context must not keep a half-built authentication around for the next request on this
			// thread, and the problem detail is written here because the advice never sees filter failures.
			SecurityContextHolder.clearContext();
			writer.write(request, response, ex.errorType(), ex.getMessage());
			return;
		}
		chain.doFilter(request, response);
	}

	private String bearerToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			return null;
		}
		String token = header.substring(BEARER_PREFIX.length()).trim();
		return token.isEmpty() ? null : token;
	}
}

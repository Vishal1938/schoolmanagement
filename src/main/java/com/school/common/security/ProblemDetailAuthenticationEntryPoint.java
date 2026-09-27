package com.school.common.security;

import java.io.IOException;

import com.school.common.exceptions.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Answers 401 as a problem detail when a protected endpoint is called without authentication. */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ProblemDetailResponseWriter writer;

	public ProblemDetailAuthenticationEntryPoint(ProblemDetailResponseWriter writer) {
		this.writer = writer;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		// The exception message can name the failing credential, so a fixed detail is sent instead.
		writer.write(request, response, ErrorType.UNAUTHORIZED, "Authentication is required for this endpoint");
	}
}

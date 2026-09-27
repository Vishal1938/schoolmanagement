package com.school.common.security;

import java.io.IOException;

import com.school.common.exceptions.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Answers 403 as a problem detail when an authenticated caller lacks the required permission. */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

	private final ProblemDetailResponseWriter writer;

	public ProblemDetailAccessDeniedHandler(ProblemDetailResponseWriter writer) {
		this.writer = writer;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		writer.write(request, response, ErrorType.FORBIDDEN, "You do not have permission to perform this action");
	}
}

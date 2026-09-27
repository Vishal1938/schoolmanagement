package com.school.common.security;

import java.io.IOException;
import java.net.URI;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.common.exceptions.ErrorType;
import com.school.common.exceptions.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Writes a problem detail straight to the response for failures raised inside the security filter
 * chain, which never reach the {@code @RestControllerAdvice}. Keeping one writer means a 401 from a
 * filter is byte-for-byte the same shape as a 401 from a controller.
 */
@Component
public class ProblemDetailResponseWriter {

	private final ProblemDetailFactory problems;
	private final ObjectMapper objectMapper;

	public ProblemDetailResponseWriter(ProblemDetailFactory problems, ObjectMapper objectMapper) {
		this.problems = problems;
		this.objectMapper = objectMapper;
	}

	public void write(HttpServletRequest request, HttpServletResponse response, ErrorType errorType, String detail)
			throws IOException {
		if (response.isCommitted()) {
			return;
		}
		ProblemDetail problem = problems.create(errorType, detail);
		problem.setInstance(URI.create(request.getRequestURI()));
		response.setStatus(errorType.status().value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getOutputStream(), problem);
	}
}

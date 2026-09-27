package com.school.common.exceptions;

import java.time.Clock;
import java.time.Instant;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds the {@code ProblemDetail} bodies used by the global error handler and by the security
 * entry points, so an error raised inside a controller and one raised in a security filter look
 * identical to the client.
 */
@Component
public class ProblemDetailFactory {

	private final Clock clock;

	public ProblemDetailFactory(Clock clock) {
		this.clock = clock;
	}

	public ProblemDetail create(ErrorType errorType, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				errorType.status(), detail == null || detail.isBlank() ? errorType.title() : detail);
		problem.setType(errorType.type());
		problem.setTitle(errorType.title());
		problem.setProperty("code", errorType.code());
		problem.setProperty("timestamp", Instant.now(clock).toString());
		return problem;
	}

	/**
	 * Fills in our members on a problem detail Spring MVC produced itself (unreadable body, unknown
	 * route, wrong method, …), leaving anything already set untouched.
	 */
	public void enrich(ProblemDetail problem, HttpStatusCode statusCode) {
		ErrorType errorType = ErrorType.fromStatus(statusCode);
		if (problem.getType() == null || "about:blank".equals(problem.getType().toString())) {
			problem.setType(errorType.type());
		}
		if (problem.getTitle() == null) {
			problem.setTitle(errorType.title());
		}
		if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
			problem.setProperty("code", errorType.code());
		}
		if (problem.getProperties() == null || !problem.getProperties().containsKey("timestamp")) {
			problem.setProperty("timestamp", Instant.now(clock).toString());
		}
	}
}

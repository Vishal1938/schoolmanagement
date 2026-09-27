package com.school.common.exceptions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for every error the application raises deliberately. Carries the {@link ErrorType},
 * which decides the HTTP status, and optional extra properties that are copied onto the
 * {@code ProblemDetail}.
 *
 * <p>Messages are client-facing: never put secrets, tokens or password material in them.
 */
public class AppException extends RuntimeException {

	private final ErrorType errorType;
	private final Map<String, Object> properties;

	public AppException(ErrorType errorType, String detail) {
		this(errorType, detail, Map.of(), null);
	}

	public AppException(ErrorType errorType, String detail, Throwable cause) {
		this(errorType, detail, Map.of(), cause);
	}

	public AppException(ErrorType errorType, String detail, Map<String, Object> properties, Throwable cause) {
		super(detail, cause);
		this.errorType = errorType;
		this.properties = properties.isEmpty()
				? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(properties));
	}

	public ErrorType errorType() {
		return errorType;
	}

	/** Extra members added to the problem detail, e.g. {@code retryAfterSeconds}. */
	public Map<String, Object> properties() {
		return properties;
	}
}

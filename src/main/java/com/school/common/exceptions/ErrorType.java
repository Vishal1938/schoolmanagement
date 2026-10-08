package com.school.common.exceptions;

import java.net.URI;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * The closed set of error types the API can return. The {@code type} URI and the {@code code}
 * property of every {@link org.springframework.http.ProblemDetail} come from here, so clients can
 * branch on a stable machine-readable value instead of parsing messages.
 */
public enum ErrorType {

	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
	BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token expired"),
	TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token invalid"),
	FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
	PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "Password change required"),
	NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
	CONFLICT(HttpStatus.CONFLICT, "Conflict"),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
	PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large"),
	UNPROCESSABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Request cannot be processed"),
	/** Used for the account lockout in B2 and for exam papers requested before release in B14. */
	LOCKED(HttpStatus.LOCKED, "Locked"),
	RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),
	DEPENDENCY_FAILED(HttpStatus.BAD_GATEWAY, "Upstream dependency failed"),
	FEATURE_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "Feature disabled"),
	/**
	 * A feature switched off at deployment level where the route must not even admit to existing: the
	 * AI endpoints when {@code app.features.ai} is false (B18). Carries the same {@code code} and
	 * {@code type} as {@link #FEATURE_DISABLED} — a client branches on the code and this is the same
	 * condition — and differs only in the status, so a school without AI looks to the outside like a
	 * build that never had those routes.
	 */
	FEATURE_DISABLED_NOT_FOUND(HttpStatus.NOT_FOUND, "Feature disabled", "FEATURE_DISABLED");

	/** Base for the {@code type} URI; kept off the request host so it stays stable per deployment. */
	public static final String TYPE_BASE = "https://schoolmanagement.dev/problems/";

	private final HttpStatus status;
	private final String title;
	private final String code;
	private final URI type;

	ErrorType(HttpStatus status, String title) {
		this(status, title, null);
	}

	/**
	 * @param code the wire code, when it is deliberately not the constant name. Only for a second
	 *             member describing the same condition at a different status; the {@code type} URI is
	 *             derived from the code, so the two stay indistinguishable to a client that branches on
	 *             either.
	 */
	ErrorType(HttpStatus status, String title, String code) {
		this.status = status;
		this.title = title;
		this.code = code == null ? name() : code;
		this.type = URI.create(TYPE_BASE + this.code.toLowerCase(Locale.ROOT).replace('_', '-'));
	}

	public HttpStatus status() {
		return status;
	}

	public String title() {
		return title;
	}

	public URI type() {
		return type;
	}

	/** The value put in the {@code code} property, e.g. {@code VALIDATION_ERROR}. */
	public String code() {
		return code;
	}

	/**
	 * Fallback used when Spring MVC produces a problem detail we did not raise ourselves. Statuses
	 * shared by several types (400, 401, 403) map to the generic member, never to a specific one such
	 * as {@code INVALID_CREDENTIALS}.
	 */
	public static ErrorType fromStatus(HttpStatusCode statusCode) {
		return switch (statusCode.value()) {
			case 400 -> BAD_REQUEST;
			case 401 -> UNAUTHORIZED;
			case 403 -> FORBIDDEN;
			case 404 -> NOT_FOUND;
			case 405 -> METHOD_NOT_ALLOWED;
			case 409 -> CONFLICT;
			case 413 -> PAYLOAD_TOO_LARGE;
			case 415 -> UNSUPPORTED_MEDIA_TYPE;
			case 422 -> UNPROCESSABLE;
			case 423 -> LOCKED;
			case 429 -> RATE_LIMITED;
			case 502 -> DEPENDENCY_FAILED;
			case 503 -> FEATURE_DISABLED;
			default -> statusCode.is4xxClientError() ? BAD_REQUEST : INTERNAL_ERROR;
		};
	}
}

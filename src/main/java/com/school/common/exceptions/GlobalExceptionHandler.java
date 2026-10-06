package com.school.common.exceptions;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The single place where exceptions become HTTP responses. Every response body is a
 * {@code ProblemDetail} carrying {@code type}, {@code title}, {@code status}, {@code detail},
 * {@code instance}, {@code code} and {@code timestamp}, plus {@code errors} for validation failures.
 *
 * <p>Nothing here logs request bodies, tokens or credentials; unexpected failures are logged with a
 * generated {@code errorId} that is also returned to the client, so a report can be traced without
 * leaking internals.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final ProblemDetailFactory problems;

	public GlobalExceptionHandler(ProblemDetailFactory problems) {
		this.problems = problems;
	}

	// --- deliberate application errors ---------------------------------------------------------

	@ExceptionHandler(AppException.class)
	public ResponseEntity<ProblemDetail> handleAppException(AppException ex, HttpServletRequest request) {
		if (ex.errorType().status().is5xxServerError()) {
			log.error("{} on {} {}", ex.errorType().code(), request.getMethod(), request.getRequestURI(), ex);
		}
		else {
			log.debug("{} on {} {}: {}", ex.errorType().code(), request.getMethod(), request.getRequestURI(),
					ex.getMessage());
		}
		return respond(ex.errorType(), ex.getMessage(), ex.properties(), request);
	}

	// --- validation -----------------------------------------------------------------------------

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldViolation> violations = new ArrayList<>();
		ex.getBindingResult().getFieldErrors().forEach(error -> violations.add(toViolation(error)));
		ex.getBindingResult().getGlobalErrors()
				.forEach(error -> violations.add(new FieldViolation(error.getObjectName(), message(error.getDefaultMessage()))));
		return validationResponse(ex, violations, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldViolation> violations = new ArrayList<>();
		for (ParameterValidationResult result : ex.getParameterValidationResults()) {
			String name = result.getMethodParameter().getParameterName();
			result.getResolvableErrors().forEach(error -> violations.add(new FieldViolation(
					name == null ? "request" : name, message(error.getDefaultMessage()))));
		}
		return validationResponse(ex, violations, headers, status, request);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
			HttpServletRequest request) {
		List<FieldViolation> violations = ex.getConstraintViolations().stream()
				.map(this::toViolation)
				.sorted(Comparator.comparing(FieldViolation::field))
				.toList();
		return respond(ErrorType.VALIDATION_ERROR, "Request validation failed", Map.of("errors", violations), request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		// The parser message can echo the payload, so it is replaced with a fixed one.
		ProblemDetail problem = problems.create(ErrorType.BAD_REQUEST, "Request body is missing or malformed");
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	// --- infrastructure ------------------------------------------------------------------------

	@ExceptionHandler(DuplicateKeyException.class)
	public ResponseEntity<ProblemDetail> handleDuplicateKey(DuplicateKeyException ex, HttpServletRequest request) {
		log.debug("Duplicate key on {} {}", request.getMethod(), request.getRequestURI(), ex);
		return respond(ErrorType.CONFLICT, "The resource already exists", Map.of(), request);
	}

	/**
	 * Two admins saving the same document at once: the second save loses the optimistic lock. A 409
	 * tells the client to reload and reapply, which is true, where a 500 would suggest a server fault.
	 */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<ProblemDetail> handleOptimisticLocking(OptimisticLockingFailureException ex,
			HttpServletRequest request) {
		log.debug("Optimistic locking failure on {} {}", request.getMethod(), request.getRequestURI(), ex);
		return respond(ErrorType.CONFLICT, "Someone else changed this while you were editing it. "
				+ "Reload and apply your change again.", Map.of(), request);
	}

	@Override
	protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = problems.create(ErrorType.PAYLOAD_TOO_LARGE,
				"The uploaded file is larger than the allowed limit");
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	/**
	 * Authentication and authorization failures are rethrown on purpose: Spring Security's
	 * {@code ExceptionTranslationFilter} decides between 401 and 403 (anonymous versus authenticated),
	 * and the configured entry point and denied handler write the same problem-detail shape. Without
	 * these two methods, the catch-all below would turn them into 500s.
	 */
	@ExceptionHandler({AuthenticationException.class, AccessDeniedException.class})
	public void rethrowSecurityException(RuntimeException ex) {
		throw ex;
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
		String errorId = UUID.randomUUID().toString();
		log.error("Unhandled exception [errorId={}] on {} {}", errorId, request.getMethod(), request.getRequestURI(), ex);
		return respond(ErrorType.INTERNAL_ERROR, "Something went wrong. Quote the errorId when reporting this.",
				Map.of("errorId", errorId), request);
	}

	// --- shared plumbing -----------------------------------------------------------------------

	/** Adds {@code code} and {@code timestamp} to the problem details Spring MVC builds itself. */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem) {
			problems.enrich(problem, statusCode);
		}
		return response;
	}

	private ResponseEntity<Object> validationResponse(Exception ex, List<FieldViolation> violations,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = problems.create(ErrorType.VALIDATION_ERROR, "Request validation failed");
		problem.setProperty("errors", violations);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	private ResponseEntity<ProblemDetail> respond(ErrorType errorType, String detail, Map<String, Object> properties,
			HttpServletRequest request) {
		ProblemDetail problem = problems.create(errorType, detail);
		problem.setInstance(URI.create(request.getRequestURI()));
		properties.forEach(problem::setProperty);
		return ResponseEntity.status(errorType.status()).body(problem);
	}

	private FieldViolation toViolation(FieldError error) {
		return new FieldViolation(error.getField(), message(error.getDefaultMessage()));
	}

	private FieldViolation toViolation(ConstraintViolation<?> violation) {
		String path = violation.getPropertyPath() == null ? "request" : violation.getPropertyPath().toString();
		return new FieldViolation(path, message(violation.getMessage()));
	}

	private String message(String message) {
		return message == null || message.isBlank() ? "is invalid" : message;
	}
}

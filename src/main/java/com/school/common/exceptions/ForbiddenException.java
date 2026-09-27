package com.school.common.exceptions;

/**
 * Raised by object-level checks in services, e.g. a student reading another student's records.
 * Permission-level checks are done declaratively with {@code @PreAuthorize} instead.
 */
public class ForbiddenException extends AppException {

	public ForbiddenException(String detail) {
		super(ErrorType.FORBIDDEN, detail);
	}
}

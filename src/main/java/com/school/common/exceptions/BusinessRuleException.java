package com.school.common.exceptions;

/**
 * Raised when a request is well-formed but breaks a business rule, e.g. marks above the subject's
 * maximum, or editing attendance outside the configured window.
 */
public class BusinessRuleException extends AppException {

	public BusinessRuleException(String detail) {
		super(ErrorType.UNPROCESSABLE, detail);
	}
}

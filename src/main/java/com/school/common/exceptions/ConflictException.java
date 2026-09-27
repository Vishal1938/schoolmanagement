package com.school.common.exceptions;

/** Raised on a uniqueness or state conflict, e.g. a second active session. */
public class ConflictException extends AppException {

	public ConflictException(String detail) {
		super(ErrorType.CONFLICT, detail);
	}

	public ConflictException(String detail, Throwable cause) {
		super(ErrorType.CONFLICT, detail, cause);
	}
}

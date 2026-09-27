package com.school.common.exceptions;

/** Raised when an entity does not exist, or when the caller may not know that it exists. */
public class NotFoundException extends AppException {

	public NotFoundException(String detail) {
		super(ErrorType.NOT_FOUND, detail);
	}

	/** Convenience for the common "Student STU-2026-0001 not found" shape. */
	public static NotFoundException of(String entityType, Object id) {
		return new NotFoundException(entityType + " " + id + " not found");
	}
}

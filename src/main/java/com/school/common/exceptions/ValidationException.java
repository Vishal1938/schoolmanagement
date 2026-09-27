package com.school.common.exceptions;

import java.util.List;
import java.util.Map;

/**
 * Raised for validation that Bean Validation cannot express, and for bulk imports where every
 * offending row has to be reported at once (B5/B6 validate the whole sheet before writing anything).
 */
public class ValidationException extends AppException {

	public ValidationException(String detail, List<FieldViolation> violations) {
		super(ErrorType.VALIDATION_ERROR, detail, Map.of("errors", List.copyOf(violations)), null);
	}

	@SuppressWarnings("unchecked")
	public List<FieldViolation> violations() {
		return (List<FieldViolation>) properties().getOrDefault("errors", List.of());
	}
}

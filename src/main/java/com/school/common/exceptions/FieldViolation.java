package com.school.common.exceptions;

/**
 * One entry of the {@code errors} array on a validation problem detail.
 *
 * @param field   the offending field, dotted for nested paths, or {@code row.3.dob} for imports
 * @param message what is wrong with it
 */
public record FieldViolation(String field, String message) {
}

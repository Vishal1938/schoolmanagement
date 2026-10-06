package com.school.people.app;

import com.school.people.domain.EmployeeStatus;
import com.school.people.domain.EmployeeType;

/**
 * The filters of {@code GET /employees}. All optional, all ANDed.
 *
 * @param q free text matched against the name, the uniqueId and the phone number, as a prefix
 */
public record EmployeeSearch(EmployeeType type, EmployeeStatus status, String q) {
}

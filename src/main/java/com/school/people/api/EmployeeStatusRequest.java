package com.school.people.api;

import com.school.people.domain.EmployeeStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /employees/{uniqueId}/status}.
 *
 * @param reason optional note kept in the audit trail — "resigned", "retired", "contract ended"
 */
public record EmployeeStatusRequest(
		@NotNull EmployeeStatus status,
		@Size(max = 200) String reason) {
}

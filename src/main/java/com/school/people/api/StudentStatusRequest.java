package com.school.people.api;

import com.school.people.domain.StudentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /students/{uniqueId}/status}.
 *
 * @param reason optional note kept in the audit trail — "transferred to Delhi", "completed Class 12"
 */
public record StudentStatusRequest(
		@NotNull StudentStatus status,
		@Size(max = 200) String reason) {
}

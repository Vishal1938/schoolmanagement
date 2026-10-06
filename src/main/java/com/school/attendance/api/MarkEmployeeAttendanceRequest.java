package com.school.attendance.api;

import java.util.List;

import com.school.attendance.domain.AttendanceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /attendance/employees?date=}: the whole staff register for one day. A replace,
 * not a merge, for the same reasons as the student one.
 *
 * <p>Every {@code employeeUniqueId} must be an ACTIVE employee.
 */
public record MarkEmployeeAttendanceRequest(@Valid @NotNull @Size(max = 500) List<Entry> entries) {

	public record Entry(
			@NotBlank String employeeUniqueId,
			@NotNull AttendanceStatus status) {
	}
}

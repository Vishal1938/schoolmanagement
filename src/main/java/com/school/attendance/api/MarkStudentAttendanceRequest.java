package com.school.attendance.api;

import java.util.List;

import com.school.attendance.domain.AttendanceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /attendance/class/{classId}/{section}}: the whole register for one day.
 *
 * <p>A replace, not a merge. A student left out of {@code entries} ends the call unmarked, which is
 * what lets a correction remove a mark rather than only change one, and what makes the
 * before-and-after in the audit trail readable.
 *
 * <p>Every {@code studentUniqueId} must be an ACTIVE student of that class-section — the register
 * the GET returned. An unknown or out-of-section id fails the whole request.
 */
public record MarkStudentAttendanceRequest(@Valid @NotNull @Size(max = 200) List<Entry> entries) {

	public record Entry(
			@NotBlank String studentUniqueId,
			@NotNull AttendanceStatus status) {
	}
}

package com.school.attendance.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.school.attendance.domain.AttendanceStatus;

/**
 * One day's staff register: every ACTIVE employee by name, with their mark or {@code null} where
 * nobody has marked them yet. Same shape and same rules as the class register.
 */
public record EmployeeRegisterResponse(
		LocalDate date,
		boolean marked,
		boolean editable,
		String lockedReason,
		String markedBy,
		Instant markedAt,
		List<Row> entries) {

	/** @param status null when this employee has no mark for the day */
	public record Row(String employeeUniqueId, String name, String employeeType, AttendanceStatus status) {
	}
}

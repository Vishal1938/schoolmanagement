package com.school.attendance.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One day's register for the whole staff. There is only one per date — employees are not split into
 * sections — so the date itself is the unique key.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(EmployeeAttendance.COLLECTION)
@CompoundIndex(name = "employee_attendance_employee_date_idx",
		def = "{'entries.employeeUniqueId': 1, 'date': 1}")
public class EmployeeAttendance {

	public static final String COLLECTION = "employee_attendance";

	@Id
	private String id;

	@Indexed(name = "employee_attendance_date_idx", unique = true)
	private LocalDate date;

	private List<EmployeeAttendanceEntry> entries;

	private String markedBy;

	private Instant markedAt;

	private Instant createdAt;
}

package com.school.attendance.domain;

/** One employee's mark on one day. */
public record EmployeeAttendanceEntry(String employeeUniqueId, AttendanceStatus status) {
}

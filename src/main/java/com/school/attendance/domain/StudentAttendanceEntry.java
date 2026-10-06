package com.school.attendance.domain;

/** One student's mark on one day. */
public record StudentAttendanceEntry(String studentUniqueId, AttendanceStatus status) {
}

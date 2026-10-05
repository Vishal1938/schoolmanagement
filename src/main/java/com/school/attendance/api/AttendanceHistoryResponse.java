package com.school.attendance.api;

import java.time.LocalDate;
import java.util.List;

import com.school.attendance.domain.AttendanceStatus;

/**
 * One person's attendance over a date range: the marked days, oldest first, and the totals.
 *
 * <p>Only days that were actually marked appear. Holidays, weekends and days nobody submitted are
 * simply absent from {@code days} — see {@link AttendanceSummary} for why they do not count against
 * anybody either.
 */
public record AttendanceHistoryResponse(
		String uniqueId,
		String name,
		LocalDate from,
		LocalDate to,
		List<Day> days,
		AttendanceSummary summary) {

	public record Day(LocalDate date, AttendanceStatus status) {
	}
}

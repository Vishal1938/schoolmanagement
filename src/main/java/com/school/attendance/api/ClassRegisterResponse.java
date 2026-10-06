package com.school.attendance.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.school.attendance.domain.AttendanceStatus;

/**
 * One day's register for a class-section: every ACTIVE student in roll order, with their mark or
 * {@code null} where nobody has marked them yet.
 *
 * <p>The register is always the <em>current</em> roll of the class, not whoever happened to be in
 * the stored document. A student admitted since the day was marked therefore appears with a null
 * status rather than being invisible.
 *
 * @param marked   whether a register has been submitted for this date at all
 * @param editable whether <em>this caller</em> may still submit or correct it, which for a teacher
 *                 depends on the edit window. The UI uses it to decide between a form and a
 *                 read-only view; the backend checks it again on the PUT regardless
 * @param lockedReason why {@code editable} is false, in words that can be shown to the user; null
 *                 when it is true
 */
public record ClassRegisterResponse(
		String classId,
		String className,
		String section,
		LocalDate date,
		boolean marked,
		boolean editable,
		String lockedReason,
		String markedBy,
		Instant markedAt,
		List<Row> entries) {

	/** @param status null when this student has no mark for the day */
	public record Row(String studentUniqueId, String name, int rollNo, AttendanceStatus status) {
	}
}

package com.school.exams.app;

import java.time.LocalDate;

import com.school.attendance.app.AttendanceShare;
import com.school.exams.api.ExamResult;
import com.school.people.app.StudentRef;
import com.school.schoolconfig.domain.ContactDetails;
import com.school.schoolconfig.domain.Identity;

/**
 * Everything one report card prints, gathered before a single byte of PDF is written.
 *
 * <p>The renderer takes this and nothing else: it reads no database and calls no service, so what
 * ends up on a report card is decided in {@link ReportCardService}, where the authorization was, and
 * not somewhere inside a layout routine.
 *
 * @param classTeacherName    null when the section has no class teacher, or they have since left; the
 *                            line is still printed, just without a name under it
 * @param principalName       null when the school has not named a principal
 * @param footer              the report-card footer from the configuration; may be null
 */
public record ReportCardData(
		Identity identity,
		ContactDetails contact,
		String sessionName,
		String className,
		StudentRef student,
		ExamResult result,
		AttendanceShare attendance,
		String classTeacherName,
		String principalName,
		String principalDesignation,
		String footer,
		LocalDate generatedOn) {
}

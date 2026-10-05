package com.school.exams.api;

import java.util.List;

import com.school.exams.domain.ExamStatus;

/**
 * The marks-entry grid: one subject, one class-section, every ACTIVE student in roll order with
 * their mark or null.
 *
 * <p>Rows come from the current roll rather than from the stored marks, so a student admitted since
 * the grid was last saved appears unmarked instead of being invisible.
 *
 * @param editable whether marks may be written right now, which means the exam is in MARKS_ENTRY.
 *                 The UI uses it to decide between a form and a read-only grid; the backend checks
 *                 it again on the PUT regardless
 */
public record MarksEntryResponse(
		String examId,
		String examName,
		ExamStatus status,
		boolean editable,
		String lockedReason,
		String classId,
		String className,
		String section,
		String subjectId,
		String subjectName,
		int maxMarks,
		int passMarks,
		List<Row> entries) {

	public record Row(
			String studentUniqueId,
			String name,
			int rollNo,
			Integer marksObtained,
			boolean absent,
			String remarks,
			String enteredBy) {
	}
}

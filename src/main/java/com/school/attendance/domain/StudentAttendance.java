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
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One day's register for one class-section: every student's mark in a single document.
 *
 * <p>A document per (class, section, date) rather than one per student per day, because that is how
 * attendance is both written and read — a teacher submits a whole class at once, and the class view
 * is one lookup instead of forty. A school of a thousand students generates a few hundred of these
 * a year rather than two hundred thousand.
 *
 * <p>The price is the per-student history, which has to reach inside the array. That is what the
 * second index is for.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(StudentAttendance.COLLECTION)
// Unique: one register per class-section per day. Two teachers submitting at once cannot create a
// second document and silently halve the class.
@CompoundIndex(name = "student_attendance_class_section_date_idx",
		def = "{'classId': 1, 'section': 1, 'date': 1}", unique = true)
// The per-student history query, which is otherwise a collection scan.
@CompoundIndex(name = "student_attendance_student_date_idx",
		def = "{'entries.studentUniqueId': 1, 'date': 1}")
public class StudentAttendance {

	public static final String COLLECTION = "student_attendance";

	@Id
	private String id;

	private String classId;

	private String section;

	private LocalDate date;

	/** One entry per student who was on the register when it was submitted. */
	private List<StudentAttendanceEntry> entries;

	/** {@code uniqueId} of whoever last submitted it. Any teacher may mark any class. */
	private String markedBy;

	/** When it was last submitted. The teacher's edit window is counted from here. */
	private Instant markedAt;

	private Instant createdAt;
}

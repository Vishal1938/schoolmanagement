package com.school.exams.domain;

import java.time.Instant;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One student's mark in one subject of one exam.
 *
 * <p>A document per mark rather than a bucket per student or per subject, unlike attendance. Marks
 * are written one grid at a time but read in two directions — down a subject for a teacher, across
 * the subjects for a report card — and neither bucketing serves both. There are also far fewer of
 * them than attendance records: a few per student per term, not one a day.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(Mark.COLLECTION)
// Unique: one mark per student per subject per exam. This is what makes the bulk entry an upsert
// rather than a source of duplicates when a teacher submits the same grid twice.
@CompoundIndex(name = "marks_exam_student_subject_idx",
		def = "{'examId': 1, 'studentUniqueId': 1, 'subjectId': 1}", unique = true)
// Reading across a student's subjects: report cards, results, the publish check.
@CompoundIndex(name = "marks_exam_student_idx", def = "{'examId': 1, 'studentUniqueId': 1}")
// Reading down one subject: the marks-entry grid.
@CompoundIndex(name = "marks_exam_subject_idx", def = "{'examId': 1, 'subjectId': 1}")
public class Mark {

	public static final String COLLECTION = "marks";

	@Id
	private String id;

	private String examId;

	private String studentUniqueId;

	private String subjectId;

	/**
	 * Null when the student was absent, and null is also what an unmarked paper looks like — the
	 * difference is {@link #absent}, which is deliberate rather than missing.
	 */
	private Integer marksObtained;

	private boolean absent;

	/** Free text from the teacher, e.g. "missed the practical". Shown on the report card. */
	private String remarks;

	/** {@code uniqueId} of whoever last entered it. Any teacher may enter marks for any class. */
	private String enteredBy;

	private Instant enteredAt;

	private Instant createdAt;
}

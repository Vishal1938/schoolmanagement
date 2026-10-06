package com.school.people.api;

import com.school.common.fees.FeeStatus;
import com.school.people.domain.Enrollment;
import com.school.people.domain.Student;

/**
 * One row of {@code GET /students}.
 *
 * <p>Deliberately one shape for both callers rather than a per-role row: it is already within what a
 * teacher may see, so there is no projection to get wrong on a list that an admin and a teacher hit
 * with the same query. An admin who needs more opens the student.
 */
public record StudentListItem(
		String uniqueId,
		String name,
		String photoUrl,
		String classId,
		String className,
		String section,
		int rollNo,
		String guardianPhone,
		String status,
		FeeStatus feeStatus) {

	public static StudentListItem of(Student student, String className, FeeStatus feeStatus) {
		Enrollment enrollment = student.getEnrollment();
		return new StudentListItem(
				student.getUniqueId(),
				student.getName(),
				student.getPhotoUrl(),
				enrollment == null ? null : enrollment.classId(),
				className,
				enrollment == null ? null : enrollment.section(),
				enrollment == null ? 0 : enrollment.rollNo(),
				student.getGuardians() == null ? null : student.getGuardians().phone(),
				student.getStatus() == null ? null : student.getStatus().name(),
				feeStatus);
	}
}

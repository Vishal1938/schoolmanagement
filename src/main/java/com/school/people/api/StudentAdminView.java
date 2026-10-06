package com.school.people.api;

import java.time.Instant;
import java.time.LocalDate;

import com.school.common.fees.FeeStatus;
import com.school.people.domain.Address;
import com.school.people.domain.Enrollment;
import com.school.people.domain.Gender;
import com.school.people.domain.Guardians;
import com.school.people.domain.Student;
import com.school.people.domain.StudentStatus;

/**
 * Everything about a student, for an administrator.
 *
 * <p>{@code feeStatus} is here as a summary; invoices and amounts are their own endpoints under
 * {@code FEE_READ_FULL} (B11), not fields on a student.
 *
 * @param className resolved from {@code enrollment.classId} so the UI does not have to join
 */
public record StudentAdminView(
		String id,
		String uniqueId,
		String name,
		LocalDate dob,
		Gender gender,
		String photoUrl,
		String admissionNo,
		LocalDate admissionDate,
		EnrollmentView enrollment,
		String className,
		Guardians guardians,
		Address address,
		String bloodGroup,
		String previousSchool,
		StudentStatus status,
		FeeStatus feeStatus,
		Instant createdAt,
		Instant updatedAt) implements StudentView {

	public record EnrollmentView(String sessionId, String classId, String section, int rollNo) {
	}

	public static StudentAdminView of(Student student, String className, FeeStatus feeStatus) {
		return new StudentAdminView(
				student.getId(),
				student.getUniqueId(),
				student.getName(),
				student.getDob(),
				student.getGender(),
				student.getPhotoUrl(),
				student.getAdmissionNo(),
				student.getAdmissionDate(),
				enrollmentOf(student),
				className,
				student.getGuardians(),
				student.getAddress(),
				student.getBloodGroup(),
				student.getPreviousSchool(),
				student.getStatus(),
				feeStatus,
				student.getCreatedAt(),
				student.getUpdatedAt());
	}

	static EnrollmentView enrollmentOf(Student student) {
		Enrollment enrollment = student.getEnrollment();
		return enrollment == null ? null : new EnrollmentView(enrollment.sessionId(), enrollment.classId(),
				enrollment.section(), enrollment.rollNo());
	}
}

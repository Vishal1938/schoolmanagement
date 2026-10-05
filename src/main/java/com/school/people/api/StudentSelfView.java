package com.school.people.api;

import java.time.LocalDate;

import com.school.common.fees.FeeStatus;
import com.school.people.domain.Address;
import com.school.people.domain.Gender;
import com.school.people.domain.Guardians;
import com.school.people.domain.Student;
import com.school.people.domain.StudentStatus;

/**
 * What a student (or the parent signing in as them) sees about themselves: their whole record.
 *
 * <p>Nearly the same content as {@link StudentAdminView} today, but a separate type on purpose — the
 * two diverge as soon as there is anything a school records about a student that the student should
 * not read back, and the admin view additionally grows fee amounts for <em>other</em> people in B11.
 * The Mongo {@code id} and the audit timestamps are left out as internals.
 */
public record StudentSelfView(
		String uniqueId,
		String name,
		LocalDate dob,
		Gender gender,
		String photoUrl,
		String admissionNo,
		LocalDate admissionDate,
		StudentAdminView.EnrollmentView enrollment,
		String className,
		Guardians guardians,
		Address address,
		String bloodGroup,
		String previousSchool,
		StudentStatus status,
		FeeStatus feeStatus) implements StudentView {

	public static StudentSelfView of(Student student, String className, FeeStatus feeStatus) {
		return new StudentSelfView(
				student.getUniqueId(),
				student.getName(),
				student.getDob(),
				student.getGender(),
				student.getPhotoUrl(),
				student.getAdmissionNo(),
				student.getAdmissionDate(),
				StudentAdminView.enrollmentOf(student),
				className,
				student.getGuardians(),
				student.getAddress(),
				student.getBloodGroup(),
				student.getPreviousSchool(),
				student.getStatus(),
				feeStatus);
	}
}

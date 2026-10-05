package com.school.people.api;

import java.time.LocalDate;

import com.school.common.fees.FeeStatus;
import com.school.people.domain.Enrollment;
import com.school.people.domain.Gender;
import com.school.people.domain.Guardians;
import com.school.people.domain.Student;

/**
 * What a teacher may see: who the student is, where they sit, how to reach the family, and whether
 * fees are outstanding.
 *
 * <p>This record has <strong>no field for an amount, an invoice or a payment</strong>, which is the
 * point — CLAUDE.md rule 2 is enforced by the type, not by remembering to null something out.
 * {@code address}, {@code admissionNo}, {@code previousSchool} and the timestamps are left out as
 * administrative detail a class teacher has no need of.
 *
 * @param guardians names and phone numbers only; no occupation, which is admissions data
 */
public record StudentTeacherView(
		String uniqueId,
		String name,
		LocalDate dob,
		Gender gender,
		String photoUrl,
		String className,
		String section,
		int rollNo,
		GuardianContact guardians,
		String bloodGroup,
		FeeStatus feeStatus) implements StudentView {

	public record GuardianContact(
			String fatherName,
			String motherName,
			String guardianName,
			String phone,
			String altPhone,
			String email) {
	}

	public static StudentTeacherView of(Student student, String className, FeeStatus feeStatus) {
		Enrollment enrollment = student.getEnrollment();
		Guardians guardians = student.getGuardians();
		return new StudentTeacherView(
				student.getUniqueId(),
				student.getName(),
				student.getDob(),
				student.getGender(),
				student.getPhotoUrl(),
				className,
				enrollment == null ? null : enrollment.section(),
				enrollment == null ? 0 : enrollment.rollNo(),
				guardians == null ? null : new GuardianContact(
						guardians.fatherName(),
						guardians.motherName(),
						guardians.guardianName(),
						guardians.phone(),
						guardians.altPhone(),
						guardians.email()),
				student.getBloodGroup(),
				feeStatus);
	}
}

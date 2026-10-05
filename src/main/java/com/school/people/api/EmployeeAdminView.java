package com.school.people.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.school.people.domain.Address;
import com.school.people.domain.Employee;
import com.school.people.domain.EmployeeStatus;
import com.school.people.domain.EmployeeType;
import com.school.people.domain.Gender;

/**
 * Everything about an employee, for an administrator, with the bank account number decrypted.
 *
 * <p>The type-specific halves are null for the other type: a STAFF member has no
 * {@code qualification}, a TEACHER no {@code designation}.
 */
public record EmployeeAdminView(
		String id,
		String uniqueId,
		EmployeeType employeeType,
		String name,
		LocalDate dob,
		Gender gender,
		String phone,
		String email,
		Address address,
		LocalDate joiningDate,
		String photoUrl,
		EmployeeStatus status,
		BankView bank,
		String panLast4,
		String qualification,
		List<String> subjectIds,
		Integer experienceYears,
		String designation,
		boolean hasLogin,
		Instant createdAt,
		Instant updatedAt) implements EmployeeView {

	/** @param accountNumber decrypted by the service, or null when no bank details are on file */
	public static EmployeeAdminView of(Employee employee, String accountNumber) {
		return new EmployeeAdminView(
				employee.getId(),
				employee.getUniqueId(),
				employee.getEmployeeType(),
				employee.getName(),
				employee.getDob(),
				employee.getGender(),
				employee.getPhone(),
				employee.getEmail(),
				employee.getAddress(),
				employee.getJoiningDate(),
				employee.getPhotoUrl(),
				employee.getStatus(),
				BankView.of(employee.getBank(), accountNumber),
				employee.getPanLast4(),
				employee.getQualification(),
				employee.getSubjectIds(),
				employee.getExperienceYears(),
				employee.getDesignation(),
				employee.isHasLogin(),
				employee.getCreatedAt(),
				employee.getUpdatedAt());
	}
}

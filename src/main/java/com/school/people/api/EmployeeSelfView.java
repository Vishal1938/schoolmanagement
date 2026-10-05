package com.school.people.api;

import java.time.LocalDate;
import java.util.List;

import com.school.people.domain.Address;
import com.school.people.domain.Employee;
import com.school.people.domain.EmployeeStatus;
import com.school.people.domain.EmployeeType;
import com.school.people.domain.Gender;

/**
 * What an employee sees about themselves. Their own record, including their own bank account
 * number — it is theirs — but without the Mongo {@code id} and the audit timestamps.
 *
 * <p>A separate type from {@link EmployeeAdminView} rather than a reuse, for the same reason the
 * student projections are separate: the two diverge the moment the school records something about
 * an employee that the employee should not read back.
 */
public record EmployeeSelfView(
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
		String designation) implements EmployeeView {

	public static EmployeeSelfView of(Employee employee, String accountNumber) {
		return new EmployeeSelfView(
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
				employee.getDesignation());
	}
}

package com.school.people.api;

import java.time.LocalDate;

import com.school.people.domain.Employee;
import com.school.people.domain.EmployeeStatus;
import com.school.people.domain.EmployeeType;

/**
 * One row of {@code GET /employees}.
 *
 * <p>The account number appears here only as {@code bankAccountLast4} — the stored last four digits,
 * never the decrypted number. That is not just a display choice: a list of fifty employees would
 * otherwise mean fifty decryptions and fifty account numbers on the wire for a screen that only ever
 * shows "ending 4821".
 */
public record EmployeeListItem(
		String uniqueId,
		EmployeeType employeeType,
		String name,
		String phone,
		String email,
		String photoUrl,
		LocalDate joiningDate,
		EmployeeStatus status,
		String designation,
		String bankAccountLast4,
		boolean hasLogin) {

	public static EmployeeListItem of(Employee employee) {
		return new EmployeeListItem(
				employee.getUniqueId(),
				employee.getEmployeeType(),
				employee.getName(),
				employee.getPhone(),
				employee.getEmail(),
				employee.getPhotoUrl(),
				employee.getJoiningDate(),
				employee.getStatus(),
				employee.getDesignation(),
				employee.getBank() == null ? null : employee.getBank().accountNumberLast4(),
				employee.isHasLogin());
	}
}

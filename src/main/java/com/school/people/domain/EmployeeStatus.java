package com.school.people.domain;

/**
 * Whether an employee still works here. Employees are never deleted — payroll runs, salary slips and
 * the marks they entered all point at them — so leaving is a status change.
 */
public enum EmployeeStatus {

	ACTIVE,

	/** Resigned, retired or dismissed. Their login is disabled with the status. */
	LEFT
}

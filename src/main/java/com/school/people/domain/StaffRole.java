package com.school.people.domain;

/**
 * Which login a staff member gets, where they get one at all.
 *
 * <p>Deliberately a two-member enum of its own rather than {@link com.school.common.security.Role}:
 * the employee screen may appoint an accountant and must not be able to appoint an administrator, and
 * a request body carrying the whole role enum would invite exactly that. The mapping to the login's
 * role is in {@code EmployeeService}.
 *
 * <p>Null, and anything on a teacher, reads as {@link #STAFF} — the employee documents written before
 * this existed have no field for it, and that is what they meant.
 */
public enum StaffRole {

	/** The default: notices and their own payslip, and nothing else. */
	STAFF,

	/**
	 * The office that handles money: fees, the expense book, and the salary register read-only. Not an
	 * administrator — no school configuration and no user management.
	 */
	ACCOUNTANT
}

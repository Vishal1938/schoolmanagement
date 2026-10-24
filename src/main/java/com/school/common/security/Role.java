package com.school.common.security;

/**
 * The roles from API_CONTRACT.md §2. There are no parent accounts: a parent signs in as the student
 * and identifies themselves by the "paid by" name on a receipt.
 *
 * <p>A role is only ever a shorthand for a set of permissions ({@link RolePermissions}). Nothing —
 * neither the filter chain, nor {@code @PreAuthorize}, nor the frontend — authorises on the role name
 * itself.
 */
public enum Role {

	/** Full access to the deployment. Created once from the {@code BOOTSTRAP_ADMIN_*} variables. */
	ADMIN,

	TEACHER,

	STUDENT,

	/** Non-teaching employee. Only gets a login when {@code hasLogin} is set on the employee (B6). */
	STAFF,

	/**
	 * The office that handles money: fees, the expense book and the salary register, and nothing else.
	 *
	 * <p>Not a kind of employee — an accountant is a STAFF member with an EMP id, hired exactly like
	 * any other, whose login carries this role instead of {@link #STAFF}. That is the distinction
	 * {@code EmployeeType} already draws: the type is what somebody does, the role is what their
	 * account may do.
	 *
	 * <p>Deliberately not an administrator: no school configuration, no user management, no payroll
	 * runs. See {@link RolePermissions}.
	 */
	ACCOUNTANT
}

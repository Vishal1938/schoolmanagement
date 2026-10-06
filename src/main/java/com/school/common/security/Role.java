package com.school.common.security;

/**
 * The four roles from API_CONTRACT.md §2. There are no parent accounts: a parent signs in as the
 * student and identifies themselves by the "paid by" name on a receipt.
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
	STAFF
}

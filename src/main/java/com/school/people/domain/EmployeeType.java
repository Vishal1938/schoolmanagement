package com.school.people.domain;

/**
 * What kind of employee this is. It decides which half of the document applies and whether a login
 * is created automatically.
 *
 * <p>Not the same thing as the login's {@link com.school.common.security.Role}: the type is what the
 * person does, the role is what their account may do. A teacher always has both; a staff member has
 * a role only if {@code hasLogin} is set.
 */
public enum EmployeeType {

	/** Teaches classes. Always gets a login with the TEACHER role. */
	TEACHER,

	/** Everyone else — accountant, driver, librarian, peon. A login only on request. */
	STAFF
}

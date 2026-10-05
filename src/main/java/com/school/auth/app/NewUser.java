package com.school.auth.app;

import com.school.common.security.Role;

/**
 * What {@link UserService#create} needs to issue a login. The {@code uniqueId} is not here: it is
 * generated, never chosen by a caller.
 *
 * @param role               decides the ID prefix (STU for a student, EMP for everyone else) and the
 *                           permission set
 * @param name               display name
 * @param email              optional; for mail only, never for logging in
 * @param rawPassword        plain password, hashed with BCrypt before it is stored and not kept anywhere
 *                           else. Callers that generated it return it to the admin exactly once
 * @param mustChangePassword true for any password the account holder did not choose themselves
 * @param profileRef         the student or employee document this login belongs to, null when there is
 *                           none yet
 */
public record NewUser(
		Role role,
		String name,
		String email,
		String rawPassword,
		boolean mustChangePassword,
		String profileRef) {
}

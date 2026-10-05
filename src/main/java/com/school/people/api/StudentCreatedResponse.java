package com.school.people.api;

/**
 * The answer to {@code POST /students}: the new record, plus the login credentials.
 *
 * <p>{@code temporaryPassword} is returned <strong>here and nowhere else, ever</strong>. Only its
 * BCrypt hash is stored, it is not written to the log or the audit trail, and no later request can
 * read it back — an admin who loses it calls
 * {@code POST /students/{uniqueId}/reset-password} for a new one.
 *
 * @param uniqueId what the student logs in with; the same ID as the student record
 */
public record StudentCreatedResponse(
		String uniqueId,
		String temporaryPassword,
		StudentAdminView student) {
}

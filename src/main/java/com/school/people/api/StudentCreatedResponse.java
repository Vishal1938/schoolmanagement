package com.school.people.api;

/**
 * The answer to {@code POST /students}: the new record, plus the login credentials.
 *
 * <p>{@code temporaryPassword} is returned <strong>here and nowhere else, ever</strong>. Only its
 * BCrypt hash is stored, it is not written to the log or the audit trail, and no later request can
 * read it back — an admin who loses it calls
 * {@code POST /students/{uniqueId}/reset-password} for a new one.
 *
 * <p>It is <strong>null</strong>, and omitted from the JSON, when the {@code studentLogin} module is
 * off: there is no account, so there is nothing to hand over. The student record is identical either
 * way, and {@code uniqueId} still identifies them on every register and receipt.
 *
 * @param uniqueId what the student logs in with, where they can; the same ID as the student record
 */
public record StudentCreatedResponse(
		String uniqueId,
		String temporaryPassword,
		StudentAdminView student) {
}

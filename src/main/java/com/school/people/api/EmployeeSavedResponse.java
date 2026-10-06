package com.school.people.api;

/**
 * The answer to {@code POST /employees} and {@code PUT /employees/{uniqueId}}.
 *
 * <p>One shape for both, because both can create a login: a teacher always gets one on admission,
 * and a staff member gets one the moment {@code hasLogin} is switched on — which may well be months
 * later.
 *
 * @param temporaryPassword the new login's one-time password, or <strong>null when no login was
 *                          created by this call</strong> — a staff member without {@code hasLogin},
 *                          or an update that did not turn it on. Returned here and nowhere else,
 *                          ever; only its BCrypt hash is stored, and it is never logged or audited
 */
public record EmployeeSavedResponse(
		String uniqueId,
		String temporaryPassword,
		EmployeeAdminView employee) {
}

package com.school.auth.app;

/**
 * A login as other modules see it: just enough to name the person and refer to them.
 *
 * <p>Deliberately not the {@code User} document — nothing outside this module should see password
 * hashes, lockout state or e-mail addresses. B6 uses it to back-fill employee records for logins
 * that already exist.
 */
public record UserRef(String uniqueId, String name) {
}

package com.school.auth.domain;

import java.time.Instant;

import com.school.common.security.AuthPrincipal;
import com.school.common.security.Role;
import com.school.common.security.RolePermissions;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A login. One per person who can sign in: the bootstrap admin, every teacher, every student, and the
 * staff whose {@code hasLogin} flag is set (B6).
 *
 * <p>There is no self-signup and no username: people log in with their {@code uniqueId}, which the
 * {@code IdGenerator} issues, and the initial password is handed out by an admin.
 *
 * <p>The password is only ever here as a BCrypt hash at strength 12. It is never returned by an
 * endpoint, never logged, and {@code AuditSanitizer} redacts it before anything reaches the audit trail.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(User.COLLECTION)
public class User {

	public static final String COLLECTION = "users";

	@Id
	private String id;

	/** The human ID people log in with, e.g. {@code DEMO-EMP-26-0001}. */
	@Indexed(name = "users_unique_id_idx", unique = true)
	private String uniqueId;

	/** Display name, shown in the UI and returned by {@code /auth/me}. */
	private String name;

	/**
	 * Optional: used for password-reset mail and notifications, not for logging in. Sparse, because
	 * students often share a guardian's address or have none, and a unique index over many nulls would
	 * reject the second such account.
	 */
	@Indexed(name = "users_email_idx", unique = true, sparse = true)
	private String email;

	private String passwordHash;

	/** Queried on every boot to decide whether the bootstrap admin is needed. */
	@Indexed(name = "users_role_idx")
	private Role role;

	/** True while the account is still on a password an admin issued. Enforced by a filter in part 2. */
	private boolean mustChangePassword;

	/** A disabled account cannot log in; accounts are disabled rather than deleted, to keep the trail. */
	private boolean enabled;

	/** Consecutive failed logins. Reset on success. Drives the lockout in part 2. */
	private int failedLoginCount;

	/** Set while the account is locked out after too many failures; null when it is not. */
	private Instant lockedUntil;

	private Instant lastLoginAt;

	/** The {@code students} or {@code employees} document this login belongs to (B5, B6). */
	private String profileRef;

	/**
	 * The security principal for this login, with the role expanded into its permissions. Everything
	 * the token and {@code /auth/me} carry comes from here, and nothing credential-related is included.
	 */
	public AuthPrincipal toPrincipal() {
		return new AuthPrincipal(id, uniqueId, name, role, RolePermissions.of(role), mustChangePassword, profileRef);
	}
}

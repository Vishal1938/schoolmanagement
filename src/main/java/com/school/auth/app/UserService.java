package com.school.auth.app;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.school.auth.domain.User;
import com.school.auth.infra.UserRepository;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.NotFoundException;
import com.school.common.id.IdGenerator;
import com.school.common.id.IdType;
import com.school.common.security.Role;
import com.school.common.security.TemporaryPasswordGenerator;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * This module's public way to create and look up logins. B5 and B6 call {@link #create} when they add a
 * student or an employee, which is why the account creation lives here rather than in {@code AuthService}.
 *
 * <p>Both the ID and the hash are produced here: a caller cannot choose a {@code uniqueId} and cannot
 * hand over a pre-hashed password, so every account in the collection has an ID from the counters
 * collection and a BCrypt-12 hash.
 */
@Service
public class UserService {

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final IdGenerator idGenerator;
	private final TemporaryPasswordGenerator temporaryPasswords;
	private final RefreshTokenService refreshTokens;
	private final AuditService audit;

	public UserService(UserRepository users, PasswordEncoder passwordEncoder, IdGenerator idGenerator,
			TemporaryPasswordGenerator temporaryPasswords, RefreshTokenService refreshTokens, AuditService audit) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.idGenerator = idGenerator;
		this.temporaryPasswords = temporaryPasswords;
		this.refreshTokens = refreshTokens;
		this.audit = audit;
	}

	/**
	 * Creates an enabled login with a freshly generated {@code uniqueId}.
	 *
	 * @return the stored user, whose {@code passwordHash} is the only trace of the password left
	 */
	public User create(NewUser request) {
		return insert(idGenerator.next(idTypeFor(request.role())), request);
	}

	/**
	 * Creates a login under an ID that has already been issued, for a profile that shares it.
	 *
	 * <p>B5 and B6 need this: a student and their login are one identity, and the student document has
	 * to be written first so that {@code profileRef} can point at it. Both writes sit in one
	 * transaction, so there is no moment where a profile exists without its login or the other way
	 * round.
	 *
	 * <p>Callers do not invent IDs — the only legitimate source is {@code IdGenerator}, which is also
	 * what makes the unique index on {@code uniqueId} hold.
	 *
	 * <p>Returns nothing on purpose. This is the one creation path used from outside the module, and
	 * handing back a {@code User} would put the document — password hash, lockout state and all —
	 * into another module's signature. Callers already know the {@code uniqueId}: they issued it.
	 */
	public void createWithUniqueId(String uniqueId, NewUser request) {
		insert(uniqueId, request);
	}

	private User insert(String uniqueId, NewUser request) {
		User user = users.insert(User.builder()
				.uniqueId(uniqueId)
				.name(request.name())
				.email(normalizeEmail(request.email()))
				.passwordHash(passwordEncoder.encode(request.rawPassword()))
				.role(request.role())
				.mustChangePassword(request.mustChangePassword())
				.enabled(true)
				.failedLoginCount(0)
				.profileRef(request.profileRef())
				.build());
		// The hash is redacted by AuditSanitizer on the way in, so the whole entity is safe to pass.
		audit.record(AuditAction.USER_CREATED, "User", user.getUniqueId(), null, user);
		return user;
	}

	public Optional<User> findByUniqueId(String uniqueId) {
		return users.findByUniqueId(normalizeUniqueId(uniqueId));
	}

	public boolean existsWithRole(Role role) {
		return users.existsByRole(role);
	}

	/**
	 * Every enabled login with this role, by name, as {@link UserRef} rather than as documents.
	 *
	 * <p>Used by B6 to back-fill employee records for the logins the local seeder already created.
	 * The teacher <em>picker</em> does not come from here — it reads the employee directory, which is
	 * the real register of who teaches.
	 */
	public List<UserRef> findByRole(Role role) {
		return users.findByRoleAndEnabledTrueOrderByNameAsc(role).stream()
				.map(user -> new UserRef(user.getUniqueId(), user.getName()))
				.toList();
	}

	/**
	 * Whether this uniqueId belongs to an enabled teacher. This is what academics checks before
	 * assigning somebody to a section, and it stays accurate because a teacher always has a login and
	 * an employee who leaves has it disabled in the same call.
	 */
	public boolean isTeacher(String uniqueId) {
		return users.existsByUniqueIdAndRoleAndEnabledTrue(normalizeUniqueId(uniqueId), Role.TEACHER);
	}

	/** Whether this uniqueId has a login at all, enabled or not. */
	public boolean exists(String uniqueId) {
		return findByUniqueId(uniqueId).isPresent();
	}

	/**
	 * Whether an address is already somebody's login. The unique index on {@code users.email} is what
	 * actually enforces this; the check exists so the employee import (B6) can report a clash as a row
	 * of the spreadsheet rather than letting the insert fail halfway through the file.
	 */
	public boolean emailTaken(String email) {
		String normalized = normalizeEmail(email);
		return normalized != null && users.existsByEmail(normalized);
	}

	/**
	 * Enables or disables a login. Disabling is how an employee who has left loses access; the account
	 * is kept rather than deleted, so the audit trail and payroll history stay readable. Disabling also
	 * ends their sessions, since an access token already issued is not revocable.
	 */
	public void setEnabled(String uniqueId, boolean enabled) {
		findByUniqueId(uniqueId).ifPresent(user -> {
			users.updateEnabled(user.getId(), enabled);
			if (!enabled) {
				refreshTokens.revokeAllForUser(user.getId());
			}
		});
	}

	/**
	 * Issues a new temporary password and forces a change on next login. Every refresh token of that
	 * account is revoked: a reset exists for the case where somebody else has the old password, so
	 * leaving their sessions alive would defeat it.
	 *
	 * @return the plain password, to be shown to the admin once. It is not logged and not audited —
	 *         only the fact of the reset is
	 */
	public String resetTemporaryPassword(String uniqueId) {
		User user = findByUniqueId(uniqueId)
				.orElseThrow(() -> NotFoundException.of("User", normalizeUniqueId(uniqueId)));
		String rawPassword = temporaryPasswords.generate();
		users.resetPassword(user.getId(), passwordEncoder.encode(rawPassword));
		refreshTokens.revokeAllForUser(user.getId());
		audit.record(AuditAction.PASSWORD_RESET, "User", user.getUniqueId(),
				"An administrator issued a new temporary password");
		return rawPassword;
	}

	/**
	 * Renames the login to match its profile. Called when a student or employee is renamed, because
	 * the display name in the token and in {@code /auth/me} comes from the login, not the profile.
	 */
	public void rename(String uniqueId, String name) {
		findByUniqueId(uniqueId).ifPresent(user -> users.updateName(user.getId(), name));
	}

	/** IDs are uppercase (API_CONTRACT.md §4), so a login typed in lower case still works. */
	public static String normalizeUniqueId(String uniqueId) {
		return uniqueId == null ? null : uniqueId.trim().toUpperCase(Locale.ROOT);
	}

	private static String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			// Null rather than "", so the sparse unique index skips accounts without an address.
			return null;
		}
		return email.trim().toLowerCase(Locale.ROOT);
	}

	/** Teachers, staff and admins all share the EMP series; only students get STU. */
	private static IdType idTypeFor(Role role) {
		return role == Role.STUDENT ? IdType.STU : IdType.EMP;
	}
}

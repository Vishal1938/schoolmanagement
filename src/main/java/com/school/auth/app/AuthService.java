package com.school.auth.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.school.auth.domain.User;
import com.school.auth.infra.UserRepository;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.AppException;
import com.school.common.exceptions.ErrorType;
import com.school.common.jwt.JwtService;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Login, refresh, logout, change-password and the current-user lookup.
 *
 * <p>Every rejected login answers the same {@code INVALID_CREDENTIALS} 401 with the same message, whether
 * the ID does not exist, the password is wrong, or the account is disabled. Telling them apart would let
 * anyone enumerate valid IDs, and a {@code uniqueId} is guessable by design — it is a school code, a type
 * and a sequence number. A locked account is the one exception: {@code LOCKED} has to be distinguishable,
 * or the account holder cannot be told to come back in a quarter of an hour.
 */
@Service
public class AuthService {

	/** Wrong passwords in a row before the account locks. */
	private static final int MAX_FAILED_ATTEMPTS = 5;

	/** How long the lock holds. Long enough to stop guessing, short enough not to need an admin. */
	private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

	/** Floor for a self-chosen password. Nothing more is enforced: length beats composition rules. */
	static final int MIN_PASSWORD_LENGTH = 8;

	private static final String INVALID_CREDENTIALS = "The unique ID or password is incorrect";

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final UserRepository users;
	private final RefreshTokenService refreshTokens;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuditService audit;
	private final Clock clock;

	public AuthService(UserRepository users, RefreshTokenService refreshTokens, PasswordEncoder passwordEncoder,
			JwtService jwtService, AuditService audit, Clock clock) {
		this.users = users;
		this.refreshTokens = refreshTokens;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Verifies the credentials and starts a session: an access token plus a new refresh-token family.
	 *
	 * @throws AppException {@code INVALID_CREDENTIALS} (401) for a bad ID, a wrong password or a disabled
	 *                      account, and {@code LOCKED} (423) while the account is locked out
	 */
	public LoginResult login(String uniqueId, String rawPassword) {
		String normalized = UserService.normalizeUniqueId(uniqueId);
		Optional<User> found = users.findByUniqueId(normalized);
		if (found.isEmpty() || !found.get().isEnabled()) {
			// No counter to increment: there is no account to lock, and a nonexistent ID must answer
			// exactly like a wrong password.
			// The log says which of the two it was even though the response must not: a 401 with nothing
			// in the console is indistinguishable from a request that never arrived.
			log.info("Login rejected for {}: {}", normalized, found.isEmpty() ? "no such account" : "account disabled");
			recordQuietly(AuditAction.LOGIN_FAILURE, normalized, "Unknown or disabled account");
			throw new AppException(ErrorType.INVALID_CREDENTIALS, INVALID_CREDENTIALS);
		}

		User user = found.get();
		Instant now = Instant.now(clock);
		if (isLocked(user, now)) {
			// Checked before the password, so a correct password does not unlock the account early and the
			// lock cannot be used as an oracle for guessing.
			log.info("Login rejected for {}: locked until {}", user.getUniqueId(), user.getLockedUntil());
			recordQuietly(AuditAction.LOGIN_FAILURE, user.getUniqueId(), "Attempt while the account was locked");
			throw lockedException(user.getLockedUntil(), now);
		}
		if (!matches(user, rawPassword)) {
			throw onWrongPassword(user, now);
		}

		users.recordSuccessfulLogin(user.getId(), now);
		AuthPrincipal principal = user.toPrincipal();
		LoginResult result = startSession(user, principal);
		log.info("Login succeeded for {} ({})", principal.uniqueId(), principal.role());
		recordQuietly(AuditAction.LOGIN_SUCCESS, principal.uniqueId(), null);
		return result;
	}

	/**
	 * Exchanges the refresh cookie for a new access token and a new refresh token. The old refresh token
	 * stops working the moment this succeeds.
	 *
	 * @throws AppException {@code TOKEN_INVALID} or {@code TOKEN_EXPIRED} (401) when the cookie cannot be
	 *                      honoured, in which case the client has to log in again
	 */
	public LoginResult refresh(String rawRefreshToken) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			throw new AppException(ErrorType.TOKEN_INVALID, "There is no refresh token on this request");
		}
		RefreshTokenService.RotatedToken rotated = refreshTokens.rotate(rawRefreshToken);
		User user = users.findById(rotated.userId())
				.filter(User::isEnabled)
				// The account was disabled or removed while the session was still alive.
				.orElseThrow(() -> new AppException(ErrorType.TOKEN_INVALID, "This account is no longer active"));
		AuthPrincipal principal = user.toPrincipal();
		return new LoginResult(jwtService.issue(principal), principal, rotated.rawToken());
	}

	/**
	 * Ends the session the cookie belongs to. Silent about an unknown or missing token: logging out twice,
	 * or with a cookie that has already expired, is a success.
	 *
	 * <p>Who logged out is taken from the token rather than the security context, because logout is
	 * normally called with only the cookie — the access token has usually expired by then, or the client
	 * has already dropped it.
	 */
	public void logout(String rawRefreshToken) {
		String uniqueId = refreshTokens.revokeSession(rawRefreshToken)
				.flatMap(users::findById)
				.map(User::getUniqueId)
				.or(() -> CurrentUser.principal().map(AuthPrincipal::uniqueId))
				.orElse(null);
		if (uniqueId != null) {
			recordQuietly(AuditAction.LOGOUT, uniqueId, null);
		}
	}

	/**
	 * Changes the caller's own password and starts a fresh session.
	 *
	 * <p>Every other session of this account is revoked: if the password was changed because it may have
	 * leaked, leaving the old sessions alive would defeat the point. A new access token comes back in the
	 * response because the caller's current one still carries {@code mustChangePassword: true} in its
	 * claims, and would keep being turned away by {@code PasswordChangeRequiredFilter}.
	 *
	 * @throws AppException {@code INVALID_CREDENTIALS} (401) if the current password is wrong, and
	 *                      {@code UNPROCESSABLE} (422) if the new one is the same as the old
	 */
	public LoginResult changePassword(String currentPassword, String newPassword) {
		AuthPrincipal caller = CurrentUser.require();
		User user = users.findByUniqueId(caller.uniqueId())
				.filter(User::isEnabled)
				.orElseThrow(() -> new AppException(ErrorType.TOKEN_INVALID, "This account is no longer active"));

		if (!matches(user, currentPassword)) {
			recordQuietly(AuditAction.LOGIN_FAILURE, user.getUniqueId(),
					"Wrong current password given to change-password");
			throw new AppException(ErrorType.INVALID_CREDENTIALS, "The current password is incorrect");
		}
		if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
			throw new AppException(ErrorType.UNPROCESSABLE, "The new password must be different from the current one");
		}

		users.updatePassword(user.getId(), passwordEncoder.encode(newPassword));
		refreshTokens.revokeAllForUser(user.getId());
		// Only the fact is recorded. Neither password, nor either hash, goes anywhere near the trail.
		audit.record(AuditAction.PASSWORD_CHANGED, "User", user.getUniqueId(), "Changed by the account holder");

		User updated = users.findById(user.getId()).orElseThrow();
		AuthPrincipal principal = updated.toPrincipal();
		return startSession(updated, principal);
	}

	/**
	 * The caller's own record, re-read from the database rather than taken from the token, so
	 * {@code /auth/me} reflects a rename or a cleared {@code mustChangePassword} flag straight away
	 * instead of at the next refresh.
	 */
	public AuthPrincipal currentUser() {
		AuthPrincipal fromToken = CurrentUser.require();
		return users.findByUniqueId(fromToken.uniqueId())
				.filter(User::isEnabled)
				.map(User::toPrincipal)
				.orElseThrow(() -> new AppException(ErrorType.TOKEN_INVALID, "This account is no longer active"));
	}

	private LoginResult startSession(User user, AuthPrincipal principal) {
		return new LoginResult(
				jwtService.issue(principal), principal, refreshTokens.issueForNewSession(user.getId()));
	}

	private boolean isLocked(User user, Instant now) {
		return user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
	}

	private boolean matches(User user, String rawPassword) {
		return rawPassword != null && passwordEncoder.matches(rawPassword, user.getPasswordHash());
	}

	/**
	 * Counts the failure and locks the account once it reaches the limit.
	 *
	 * @return the exception to throw, so the caller's control flow stays obvious
	 */
	private AppException onWrongPassword(User user, Instant now) {
		int failures = user.getFailedLoginCount() + 1;
		boolean lockNow = failures >= MAX_FAILED_ATTEMPTS;
		Instant lockedUntil = lockNow ? now.plus(LOCK_DURATION) : null;
		users.recordFailedLogin(user.getId(), failures, lockedUntil);

		if (!lockNow) {
			// The attempted ID is logged and recorded, never the password that was tried with it.
			log.info("Login rejected for {}: wrong password ({} of {} before lockout)",
					user.getUniqueId(), failures, MAX_FAILED_ATTEMPTS);
			recordQuietly(AuditAction.LOGIN_FAILURE, user.getUniqueId(),
					"Wrong password (%d of %d before lockout)".formatted(failures, MAX_FAILED_ATTEMPTS));
			return new AppException(ErrorType.INVALID_CREDENTIALS, INVALID_CREDENTIALS);
		}
		recordQuietly(AuditAction.ACCOUNT_LOCKED, user.getUniqueId(),
				"Locked for %d minutes after %d failed attempts".formatted(LOCK_DURATION.toMinutes(), failures));
		log.warn("Account {} locked until {} after {} failed login attempts",
				user.getUniqueId(), lockedUntil, failures);
		return lockedException(lockedUntil, now);
	}

	/** 423 with the seconds left, so the UI can say how long rather than just "locked". */
	private AppException lockedException(Instant lockedUntil, Instant now) {
		long retryAfter = Math.max(1, Duration.between(now, lockedUntil).toSeconds());
		return new AppException(ErrorType.LOCKED,
				"Too many failed attempts. This account is locked for another %d minute(s)."
						.formatted(Math.max(1, retryAfter / 60)),
				Map.of("retryAfterSeconds", retryAfter),
				null);
	}

	/**
	 * Writes a trail entry without letting a failed write change the outcome of the request.
	 * {@code AuditService} deliberately propagates failures, but on the authentication path that would
	 * turn a clean 401 into a 500 and a successful login into an error, which is worse than a gap here.
	 */
	private void recordQuietly(AuditAction action, String uniqueId, String detail) {
		try {
			audit.record(action, "User", uniqueId, detail);
		}
		catch (RuntimeException ex) {
			log.warn("Could not write the {} audit entry for {}", action, uniqueId, ex);
		}
	}
}

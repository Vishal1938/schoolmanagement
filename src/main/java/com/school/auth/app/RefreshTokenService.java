package com.school.auth.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import com.school.auth.domain.RefreshToken;
import com.school.auth.domain.User;
import com.school.auth.infra.RefreshTokenRepository;
import com.school.auth.infra.UserRepository;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.config.AppProperties;
import com.school.common.exceptions.AppException;
import com.school.common.exceptions.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The refresh-token lifecycle: issue, rotate, revoke.
 *
 * <p>Internal to this module. The raw value is returned to {@code AuthService} exactly once, to be put in
 * the cookie, and is never stored, logged or audited — only its digest goes to the database.
 */
@Service
public class RefreshTokenService {

	/** 256 bits of randomness. Long enough that guessing is not a threat model. */
	private static final int TOKEN_BYTES = 32;

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

	private final RefreshTokenRepository tokens;
	private final UserRepository users;
	private final AuditService audit;
	private final Clock clock;
	private final Duration refreshTtl;
	private final SecureRandom random = new SecureRandom();

	public RefreshTokenService(RefreshTokenRepository tokens, UserRepository users, AuditService audit, Clock clock,
			AppProperties properties) {
		this.tokens = tokens;
		this.users = users;
		this.audit = audit;
		this.clock = clock;
		this.refreshTtl = properties.jwt().refreshTtl();
	}

	/** Starts a new family for a fresh login. */
	public String issueForNewSession(String userId) {
		return issue(userId, UUID.randomUUID().toString());
	}

	/**
	 * Verifies a presented token and replaces it with a new one in the same family.
	 *
	 * @return the user id the token belongs to, and the raw replacement value
	 * @throws AppException {@code TOKEN_INVALID} if it is unknown, already rotated or revoked;
	 *                      {@code TOKEN_EXPIRED} if it is simply past its expiry
	 */
	public RotatedToken rotate(String rawToken) {
		RefreshToken stored = tokens.findByTokenHash(hash(rawToken))
				.orElseThrow(() -> new AppException(ErrorType.TOKEN_INVALID, "The refresh token is not valid"));

		if (stored.isRotated()) {
			// The legitimate client discards a token the moment it exchanges it, so a second presentation
			// means the value leaked. Neither holder can be trusted from here on.
			long revoked = tokens.deleteByFamilyId(stored.getFamilyId());
			// Audited under the uniqueId, like every other entry about a user, so one query by uniqueId
			// returns an account's whole authentication history.
			audit.record(AuditAction.REFRESH_TOKEN_REUSE_REVOKED, "User", uniqueIdOf(stored.getUserId()),
					"A rotated refresh token was presented again; revoked " + revoked + " token(s) in the family");
			log.warn("Refresh token reuse detected for user {}; the whole token family was revoked",
					stored.getUserId());
			throw new AppException(ErrorType.TOKEN_INVALID, "This session has been revoked. Log in again.");
		}

		Instant now = Instant.now(clock);
		if (stored.getExpiresAt().isBefore(now)) {
			// Mongo's TTL sweep runs about once a minute, so an expired token can still be found here.
			tokens.delete(stored);
			throw new AppException(ErrorType.TOKEN_EXPIRED, "The refresh token has expired. Log in again.");
		}

		tokens.save(stored.toBuilder().rotatedAt(now).build());
		return new RotatedToken(stored.getUserId(), issue(stored.getUserId(), stored.getFamilyId()));
	}

	/**
	 * Ends the session a token belongs to, by deleting its whole family. Does nothing if the token is
	 * unknown — logging out with a stale cookie is a success, not an error.
	 *
	 * @return the user the session belonged to, so the caller can audit the logout. Empty when the token
	 *         was missing or already gone, in which case there is nothing to record
	 */
	public Optional<String> revokeSession(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return Optional.empty();
		}
		return tokens.findByTokenHash(hash(rawToken))
				.map(stored -> {
					tokens.deleteByFamilyId(stored.getFamilyId());
					return stored.getUserId();
				});
	}

	/** Ends every session this account has. Used after a password change. */
	public void revokeAllForUser(String userId) {
		tokens.deleteByUserId(userId);
	}

	private String issue(String userId, String familyId) {
		byte[] value = new byte[TOKEN_BYTES];
		random.nextBytes(value);
		// URL-safe and unpadded, so it is a valid cookie value with no escaping.
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(value);
		Instant now = Instant.now(clock);
		tokens.insert(RefreshToken.builder()
				.tokenHash(hash(rawToken))
				.userId(userId)
				.familyId(familyId)
				.issuedAt(now)
				.expiresAt(now.plus(refreshTtl))
				.build());
		return rawToken;
	}

	/** Falls back to the document id if the account has since been deleted: better than no entry at all. */
	private String uniqueIdOf(String userId) {
		return users.findById(userId).map(User::getUniqueId).orElse(userId);
	}

	private static String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			// SHA-256 is mandated by the JDK, so this cannot happen.
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

	/** The outcome of a rotation: who it belongs to, and the raw value to put in the new cookie. */
	public record RotatedToken(String userId, String rawToken) {
	}
}

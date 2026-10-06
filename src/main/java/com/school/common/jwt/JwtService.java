package com.school.common.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.school.common.config.AppProperties;
import com.school.common.exceptions.AppException;
import com.school.common.exceptions.ErrorType;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.Permission;
import com.school.common.security.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Signs and verifies the access token: HS256, 15 minutes by default, with the caller's role and
 * permission list in the claims so authorising a request needs no database read.
 *
 * <p>The token is a bearer credential, so nothing here ever logs it — not even a prefix, and not on the
 * failure paths, where the interesting fact is <em>why</em> it was rejected rather than what it said.
 *
 * <p>Refresh tokens are deliberately not JWTs and are not handled here: they are opaque random values
 * stored hashed, added in B2 part 2.
 */
@Service
public class JwtService {

	/** Smallest key HS256 accepts: 256 bits. A shorter secret weakens the signature, so it is refused. */
	private static final int MIN_SECRET_BYTES = 32;

	private static final String CLAIM_USER_ID = "uid";
	private static final String CLAIM_NAME = "name";
	private static final String CLAIM_ROLE = "role";
	private static final String CLAIM_PERMISSIONS = "perms";
	private static final String CLAIM_MUST_CHANGE_PASSWORD = "mcp";
	private static final String CLAIM_PROFILE_REF = "pref";

	private static final Map<String, Permission> BY_NAME = Arrays.stream(Permission.values())
			.collect(Collectors.toUnmodifiableMap(Permission::name, permission -> permission));

	private static final Logger log = LoggerFactory.getLogger(JwtService.class);

	private final SecretKey key;
	private final Duration accessTtl;
	private final Clock clock;

	public JwtService(AppProperties properties, Clock clock) {
		byte[] secret = secretBytes(properties.jwt().secret());
		// Fixed to HmacSHA256 rather than derived from the key length, so a longer secret cannot quietly
		// change the algorithm in the token header.
		this.key = new SecretKeySpec(secret, "HmacSHA256");
		this.accessTtl = properties.jwt().accessTtl();
		this.clock = clock;
	}

	/** Signs a token for this caller. */
	public AccessToken issue(AuthPrincipal principal) {
		Instant now = Instant.now(clock);
		Instant expiry = now.plus(accessTtl);
		String token = Jwts.builder()
				.subject(principal.uniqueId())
				.id(UUID.randomUUID().toString())
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiry))
				.claim(CLAIM_USER_ID, principal.userId())
				.claim(CLAIM_NAME, principal.name())
				.claim(CLAIM_ROLE, principal.role().name())
				.claim(CLAIM_PERMISSIONS, principal.permissionNames())
				.claim(CLAIM_MUST_CHANGE_PASSWORD, principal.mustChangePassword())
				.claim(CLAIM_PROFILE_REF, principal.profileRef())
				.signWith(key, Jwts.SIG.HS256)
				.compact();
		return new AccessToken(token, accessTtl.toSeconds());
	}

	/**
	 * Verifies the signature and expiry and rebuilds the principal.
	 *
	 * @throws AppException {@code TOKEN_EXPIRED} when it is merely past its expiry — the client should
	 *                      refresh and retry — and {@code TOKEN_INVALID} for anything else, which is not
	 *                      retryable
	 */
	public AuthPrincipal parse(String token) {
		try {
			Claims claims = Jwts.parser()
					.verifyWith(key)
					.clock(() -> Date.from(Instant.now(clock)))
					.build()
					.parseSignedClaims(token)
					.getPayload();
			return toPrincipal(claims);
		}
		catch (ExpiredJwtException ex) {
			throw new AppException(ErrorType.TOKEN_EXPIRED, "The access token has expired");
		}
		catch (JwtException | IllegalArgumentException ex) {
			// The message can quote parts of the token, so only the exception type is logged.
			log.debug("Rejected an access token: {}", ex.getClass().getSimpleName());
			throw new AppException(ErrorType.TOKEN_INVALID, "The access token is not valid");
		}
	}

	private AuthPrincipal toPrincipal(Claims claims) {
		Role role = Role.valueOf(claims.get(CLAIM_ROLE, String.class));
		return new AuthPrincipal(
				claims.get(CLAIM_USER_ID, String.class),
				claims.getSubject(),
				claims.get(CLAIM_NAME, String.class),
				role,
				permissions(claims),
				Boolean.TRUE.equals(claims.get(CLAIM_MUST_CHANGE_PASSWORD, Boolean.class)),
				claims.get(CLAIM_PROFILE_REF, String.class));
	}

	/**
	 * Reads the permission claim. Unknown names are dropped rather than failing the request: after a
	 * deployment that removes a permission, a token issued minutes earlier should keep working with the
	 * permissions that still exist instead of logging everyone out.
	 */
	private Set<Permission> permissions(Claims claims) {
		Set<Permission> permissions = EnumSet.noneOf(Permission.class);
		List<?> claimed = claims.get(CLAIM_PERMISSIONS, List.class);
		if (claimed == null) {
			return permissions;
		}
		for (Object name : claimed) {
			Permission permission = BY_NAME.get(String.valueOf(name));
			if (permission != null) {
				permissions.add(permission);
			}
		}
		return permissions;
	}

	private static byte[] secretBytes(String secret) {
		byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_SECRET_BYTES) {
			// Fail at startup rather than issue weakly signed tokens. The value itself is never logged.
			throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES
					+ " bytes for HS256; it is currently " + bytes.length
					+ ". Generate one with: openssl rand -base64 48");
		}
		return bytes;
	}
}

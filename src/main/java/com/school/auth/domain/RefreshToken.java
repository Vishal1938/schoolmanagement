package com.school.auth.domain;

import java.time.Instant;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One issued refresh token. Unlike the access token this is an opaque random value, not a JWT, so it can
 * be revoked — which is the whole point of it.
 *
 * <p>Only the SHA-256 of the value is stored. A stolen dump of this collection therefore cannot be
 * replayed, and the raw value exists in exactly two places: the client's cookie and the response that
 * set it. Hashing is a plain digest rather than BCrypt on purpose: the value is 256 bits of
 * {@code SecureRandom}, so there is nothing to brute-force, and a lookup has to be by exact hash.
 *
 * <h2>Rotation and families</h2>
 * Every refresh exchanges this token for a new one in the same {@link #familyId}, and stamps
 * {@link #rotatedAt} here. Presenting an already-rotated token means the value leaked — the legitimate
 * client would have discarded it — so the entire family is deleted and both the thief and the victim have
 * to log in again.
 *
 * <p>{@link #expiresAt} carries a TTL index, so Mongo removes spent and expired tokens by itself; nothing
 * in the application has to sweep this collection.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(RefreshToken.COLLECTION)
public class RefreshToken {

	public static final String COLLECTION = "refresh_tokens";

	@Id
	private String id;

	/** SHA-256 hex of the value in the cookie. Never the value itself. */
	@Indexed(name = "refresh_token_hash_idx", unique = true)
	private String tokenHash;

	private String userId;

	/** Shared by every token descended from one login, so a reuse can revoke the whole lineage. */
	@Indexed(name = "refresh_token_family_idx")
	private String familyId;

	private Instant issuedAt;

	/**
	 * When Mongo deletes this document. {@code expireAfter = "0s"} means "expire at the instant in this
	 * field" rather than a fixed delay after it.
	 */
	@Indexed(name = "refresh_token_ttl_idx", expireAfter = "0s")
	private Instant expiresAt;

	/** Set the moment this token is exchanged. A second presentation after that is a reuse. */
	private Instant rotatedAt;

	public boolean isRotated() {
		return rotatedAt != null;
	}
}

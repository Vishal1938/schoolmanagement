package com.school.auth.infra;

import java.util.Optional;

import com.school.auth.domain.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for {@code refresh_tokens}. Internal to this module: nothing outside auth ever sees a
 * refresh token.
 */
public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/** Used when a reuse is detected, and on logout: the whole lineage of one login goes at once. */
	long deleteByFamilyId(String familyId);

	/** Used after a password change, which must end every session the account has. */
	long deleteByUserId(String userId);
}

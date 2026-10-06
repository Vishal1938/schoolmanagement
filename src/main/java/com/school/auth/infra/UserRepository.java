package com.school.auth.infra;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.school.auth.domain.User;
import com.school.common.security.Role;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

/**
 * Repository for {@code users}. Internal to this module: other modules create and read logins through
 * {@code UserService}, never through here.
 */
public interface UserRepository extends MongoRepository<User, String> {

	Optional<User> findByUniqueId(String uniqueId);

	boolean existsByRole(Role role);

	boolean existsByEmail(String email);

	/** Backs the teacher picker and the assignment checks in B4. Disabled accounts are left out. */
	List<User> findByRoleAndEnabledTrueOrderByNameAsc(Role role);

	boolean existsByUniqueIdAndRoleAndEnabledTrue(String uniqueId, Role role);

	/**
	 * Stamps the login time without rewriting the whole document, so a login cannot overwrite a field
	 * another request changed in the meantime.
	 */
	@Query("{ '_id': ?0 }")
	@Update("{ '$set': { 'lastLoginAt': ?1, 'failedLoginCount': 0 }, '$unset': { 'lockedUntil': 1 } }")
	void recordSuccessfulLogin(String id, Instant at);

	/**
	 * Records a wrong password. {@code lockedUntil} is set in the same write as the counter that caused
	 * it, so the account can never be left at the limit but unlocked.
	 *
	 * @param lockedUntil when the lock lifts, or null while the account is still under the limit
	 */
	@Query("{ '_id': ?0 }")
	@Update("{ '$set': { 'failedLoginCount': ?1, 'lockedUntil': ?2 } }")
	void recordFailedLogin(String id, int failedLoginCount, Instant lockedUntil);

	/**
	 * Replaces the password and clears the forced-change flag. A targeted update rather than a whole
	 * document save, so changing a password cannot overwrite anything else that was edited meanwhile.
	 */
	@Query("{ '_id': ?0 }")
	@Update("{ '$set': { 'passwordHash': ?1, 'mustChangePassword': false, 'failedLoginCount': 0 }, "
			+ "'$unset': { 'lockedUntil': 1 } }")
	void updatePassword(String id, String passwordHash);

	/**
	 * An admin-issued password. Unlike {@link #updatePassword}, this <em>sets</em>
	 * {@code mustChangePassword}: the holder did not choose this one, so they must replace it on first
	 * use. Any lockout is lifted, which is half the point of a reset.
	 */
	@Query("{ '_id': ?0 }")
	@Update("{ '$set': { 'passwordHash': ?1, 'mustChangePassword': true, 'failedLoginCount': 0 }, "
			+ "'$unset': { 'lockedUntil': 1 } }")
	void resetPassword(String id, String passwordHash);

	/** Keeps the login's display name in step with the profile it belongs to. */
	@Query("{ '_id': ?0 }")
	@Update("{ '$set': { 'name': ?1 } }")
	void updateName(String id, String name);

	/** Disables or re-enables an account. Accounts are disabled rather than deleted, to keep the trail. */
	@Query("{ '_id': ?0 }")
	@Update("{ '$set': { 'enabled': ?1 } }")
	void updateEnabled(String id, boolean enabled);
}

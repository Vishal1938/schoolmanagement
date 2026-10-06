package com.school.academics.infra;

import java.util.List;
import java.util.Optional;

import com.school.academics.domain.AcademicSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

/**
 * Repository for {@code academic_sessions}. Internal to this module: everybody else goes through
 * {@code AcademicSessionService} or {@code AcademicContext}.
 */
public interface AcademicSessionRepository extends MongoRepository<AcademicSession, String> {

	/** Newest first, which is the order the session dropdown wants. */
	List<AcademicSession> findAllByOrderByStartDateDesc();

	Optional<AcademicSession> findFirstByActiveTrue();

	boolean existsByName(String name);

	/**
	 * Clears the flag on every other session in one write, so activating a session can never leave two
	 * active even if it is called twice at once.
	 *
	 * @return how many sessions were stood down
	 */
	@Query("{ 'active': true, '_id': { '$ne': ?0 } }")
	@Update("{ '$set': { 'active': false } }")
	long deactivateAllExcept(String id);
}

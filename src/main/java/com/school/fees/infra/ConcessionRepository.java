package com.school.fees.infra;

import java.util.List;

import com.school.fees.domain.Concession;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for {@code fee_concessions}. Internal to this module; callers use
 * {@code ConcessionService}.
 */
public interface ConcessionRepository extends MongoRepository<Concession, String> {

	List<Concession> findByStudentUniqueIdAndSessionIdOrderByCreatedAtAsc(String studentUniqueId, String sessionId);

	List<Concession> findBySessionIdOrderByCreatedAtDesc(String sessionId);

	List<Concession> findByStudentUniqueIdOrderByCreatedAtDesc(String studentUniqueId);
}

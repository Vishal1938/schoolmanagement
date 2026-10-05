package com.school.fees.infra;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.school.fees.domain.FeeStructure;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for {@code fee_structures}. Internal to this module; callers use
 * {@code FeeStructureService}.
 */
public interface FeeStructureRepository extends MongoRepository<FeeStructure, String> {

	List<FeeStructure> findBySessionIdOrderByClassIdAsc(String sessionId);

	Optional<FeeStructure> findBySessionIdAndClassId(String sessionId, String classId);

	/**
	 * The live late-fine rules behind a set of invoices, fetched in one read. A per-student ledger
	 * normally touches one structure, so this is one query either way.
	 */
	List<FeeStructure> findAllByIdIn(Collection<String> ids);
}

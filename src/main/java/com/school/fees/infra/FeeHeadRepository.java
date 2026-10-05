package com.school.fees.infra;

import java.util.Collection;
import java.util.List;

import com.school.fees.domain.FeeHead;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Repository for {@code fee_heads}. Internal to this module; callers use {@code FeeHeadService}. */
public interface FeeHeadRepository extends MongoRepository<FeeHead, String> {

	List<FeeHead> findAllByOrderByNameAsc();

	List<FeeHead> findByActiveOrderByNameAsc(boolean active);

	/** Resolving the names a structure or an invoice needs, in one read rather than one per head. */
	List<FeeHead> findAllByIdIn(Collection<String> ids);

	boolean existsByName(String name);

	boolean existsByNameAndIdNot(String name, String id);
}

package com.school.academics.infra;

import java.util.List;

import com.school.academics.domain.SchoolClass;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Repository for {@code classes}. Internal to this module; callers use {@code SchoolClassService}. */
public interface SchoolClassRepository extends MongoRepository<SchoolClass, String> {

	/** Display order, with the name as a tie-break so two classes sharing an order still sort stably. */
	Sort DISPLAY_ORDER = Sort.by("order", "name");

	/**
	 * Spelled out rather than derived from the method name: the property is called {@code order}, so a
	 * derived {@code ...OrderByOrderAsc...} is one letter away from being parsed as something else.
	 */
	default List<SchoolClass> findAllInDisplayOrder() {
		return findAll(DISPLAY_ORDER);
	}

	boolean existsByName(String name);

	boolean existsByNameAndIdNot(String name, String id);
}

package com.school.academics.infra;

import java.util.Collection;
import java.util.List;

import com.school.academics.domain.Subject;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Repository for {@code subjects}. Internal to this module; callers use {@code SubjectService}. */
public interface SubjectRepository extends MongoRepository<Subject, String> {

	List<Subject> findAllByOrderByNameAsc();

	List<Subject> findAllByIdIn(Collection<String> ids);

	boolean existsByName(String name);

	boolean existsByNameAndIdNot(String name, String id);

	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, String id);
}

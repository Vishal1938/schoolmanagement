package com.school.people.infra;

import java.util.Optional;

import com.school.people.domain.ImportJob;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for {@code import_jobs}. Internal to this module: the import services reach it through
 * {@code ImportJobService}.
 */
public interface ImportJobRepository extends MongoRepository<ImportJob, String> {

	Optional<ImportJob> findByKey(String key);
}

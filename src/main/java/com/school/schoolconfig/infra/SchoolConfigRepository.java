package com.school.schoolconfig.infra;

import com.school.schoolconfig.domain.SchoolConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for the single {@link SchoolConfig} document. Internal to this module: other modules
 * read configuration through {@code SchoolConfigService}.
 */
public interface SchoolConfigRepository extends MongoRepository<SchoolConfig, String> {
}

package com.school.common.audit;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Writes to the audit trail. Deliberately offers no delete: entries are appended and kept, and nothing
 * in the application removes them.
 */
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
}

package com.school.common.audit;

import java.time.Instant;

/**
 * Filters for reading the trail. Every field is optional; a search with all of them null returns
 * everything, newest first.
 *
 * @param entityType restrict to one entity type, e.g. {@code SchoolConfig}
 * @param entityId   restrict to one entity, normally together with {@code entityType}
 * @param action     restrict to one action
 * @param from       inclusive lower bound on the timestamp
 * @param to         exclusive upper bound on the timestamp
 */
public record AuditSearch(String entityType, String entityId, AuditAction action, Instant from, Instant to) {
}

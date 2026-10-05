package com.school.common.audit;

import java.time.Instant;
import java.util.Map;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One entry in the audit trail: who did what to which entity, when, and what the entity looked like
 * before and after.
 *
 * <h2>Retention</h2>
 * <strong>Audit entries are kept indefinitely. There is deliberately no TTL index on this collection
 * and none may be added.</strong> A trail that deletes itself cannot answer the question it exists
 * for — "who changed this mark, and when" — and for fees, payroll and exam papers that answer may be
 * needed years later. The volume is small (a few hundred entries a day for a school of this size), so
 * there is nothing to reclaim. An integration test asserts that no expiring index exists here, so this
 * cannot be undone by accident. If a deployment ever does need to shrink the collection, archive it
 * out to cold storage as part of the B19 backup kit rather than expiring documents in place.
 *
 * <p>{@code before} and {@code after} pass through {@link AuditSanitizer}, so no password, hash or
 * token can be stored here even if a caller hands over a whole entity.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(AuditLog.COLLECTION)
@CompoundIndex(name = "audit_entity_idx", def = "{'entityType': 1, 'entityId': 1}")
public class AuditLog {

	public static final String COLLECTION = "audit_logs";

	@Id
	private String id;

	private AuditAction action;

	/** Simple name of the audited entity, e.g. {@code SchoolConfig} or {@code User}. */
	private String entityType;

	/** Id of the audited entity, or the uniqueId when that is what the action was about. */
	private String entityId;

	private AuditActor actor;

	/** Descending, because the trail is always read newest first. */
	@Indexed(name = "audit_at_idx", direction = IndexDirection.DESCENDING)
	private Instant at;

	/** State before the change; null for creations and for actions with no state. */
	private Map<String, Object> before;

	/** State after the change; null for deletions and for actions with no state. */
	private Map<String, Object> after;

	/** Optional human-readable note, e.g. why a login failed. Never credential material. */
	private String detail;
}

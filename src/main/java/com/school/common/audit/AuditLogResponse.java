package com.school.common.audit;

import java.time.Instant;
import java.util.Map;

/**
 * One audit entry as returned by {@code GET /audit}. {@code before} and {@code after} are already
 * sanitized on the way in, so what is stored is what is safe to show.
 */
public record AuditLogResponse(
		String id,
		AuditAction action,
		String entityType,
		String entityId,
		Actor actor,
		Instant at,
		Map<String, Object> before,
		Map<String, Object> after,
		String detail) {

	/** {@code uniqueId} is {@code SYSTEM} for startup work and {@code ANONYMOUS} for unauthenticated calls. */
	public record Actor(String id, String uniqueId, String role) {
	}

	public static AuditLogResponse from(AuditLog log) {
		AuditActor actor = log.getActor();
		return new AuditLogResponse(
				log.getId(),
				log.getAction(),
				log.getEntityType(),
				log.getEntityId(),
				actor == null ? null : new Actor(actor.id(), actor.uniqueId(), actor.role()),
				log.getAt(),
				log.getBefore(),
				log.getAfter(),
				log.getDetail());
	}
}

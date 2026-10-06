package com.school.common.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * The single way to write and read the audit trail.
 *
 * <p>Failures are not swallowed. If the audit write fails, the operation that triggered it fails too:
 * an unrecorded change to marks, fees or payroll is worse than a failed request, and CLAUDE.md requires
 * a full trail rather than a best-effort one. Callers that must not be masked by an audit failure — the
 * authentication paths in B2, where a failed audit write should not turn a 401 into a 500 — should say
 * so explicitly at the call site rather than have this class guess.
 */
@Service
public class AuditService {

	private final AuditLogRepository repository;
	private final MongoOperations mongo;
	private final AuditSanitizer sanitizer;
	private final AuditActorResolver actorResolver;
	private final Clock clock;

	public AuditService(AuditLogRepository repository, MongoOperations mongo, AuditSanitizer sanitizer,
			AuditActorResolver actorResolver, Clock clock) {
		this.repository = repository;
		this.mongo = mongo;
		this.sanitizer = sanitizer;
		this.actorResolver = actorResolver;
		this.clock = clock;
	}

	/**
	 * Records a change. {@code before} and {@code after} are whole entities or maps; both are sanitized,
	 * so credential material cannot reach the trail.
	 *
	 * @param before state before the change, null for a creation
	 * @param after  state after the change, null for a deletion
	 */
	public AuditLog record(AuditAction action, String entityType, String entityId, Object before, Object after) {
		return write(action, entityType, entityId, before, after, null);
	}

	/** Records an action that has no before-and-after state, such as a login. */
	public AuditLog record(AuditAction action, String entityType, String entityId) {
		return write(action, entityType, entityId, null, null, null);
	}

	/** As above, with a short human-readable note. Never pass credential material as the detail. */
	public AuditLog record(AuditAction action, String entityType, String entityId, String detail) {
		return write(action, entityType, entityId, null, null, detail);
	}

	private AuditLog write(AuditAction action, String entityType, String entityId, Object before, Object after,
			String detail) {
		return repository.insert(AuditLog.builder()
				.action(action)
				.entityType(entityType)
				.entityId(entityId)
				.actor(actorResolver.currentActor())
				.at(Instant.now(clock))
				.before(sanitizer.sanitize(before))
				.after(sanitizer.sanitize(after))
				.detail(detail)
				.build());
	}

	/** Reads the trail newest first. Sorting comes from the {@code Pageable}, which defaults to {@code at} descending. */
	public Page<AuditLog> search(AuditSearch search, Pageable pageable) {
		Query query = new Query();
		List<Criteria> criteria = new ArrayList<>();
		if (search.entityType() != null && !search.entityType().isBlank()) {
			criteria.add(Criteria.where("entityType").is(search.entityType()));
		}
		if (search.entityId() != null && !search.entityId().isBlank()) {
			criteria.add(Criteria.where("entityId").is(search.entityId()));
		}
		if (search.action() != null) {
			criteria.add(Criteria.where("action").is(search.action()));
		}
		if (search.from() != null) {
			criteria.add(Criteria.where("at").gte(search.from()));
		}
		if (search.to() != null) {
			criteria.add(Criteria.where("at").lt(search.to()));
		}
		if (!criteria.isEmpty()) {
			query.addCriteria(new Criteria().andOperator(criteria));
		}

		long total = mongo.count(query, AuditLog.class);
		List<AuditLog> entries = mongo.find(query.with(pageable), AuditLog.class);
		return new PageImpl<>(entries, pageable, total);
	}
}

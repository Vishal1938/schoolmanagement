package com.school.fees.app;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.NotFoundException;
import com.school.fees.api.FeeHeadRequest;
import com.school.fees.domain.FeeHead;
import com.school.fees.infra.FeeHeadRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Fee heads — the things the school charges for.
 *
 * <p>There is no delete. Invoices name their heads, and a receipt that says "Transport" has to keep
 * saying so; retiring a head is {@code active: false}, which keeps it off new structures.
 */
@Service
public class FeeHeadService {

	private static final String AUDIT_ENTITY = "FeeHead";

	private final FeeHeadRepository heads;
	private final AuditService audit;
	private final Clock clock;

	public FeeHeadService(FeeHeadRepository heads, AuditService audit, Clock clock) {
		this.heads = heads;
		this.audit = audit;
		this.clock = clock;
	}

	/** Every head by name, or only the active ones. */
	public List<FeeHead> list(Boolean active) {
		return active == null ? heads.findAllByOrderByNameAsc() : heads.findByActiveOrderByNameAsc(active);
	}

	public FeeHead get(String id) {
		return heads.findById(id).orElseThrow(() -> NotFoundException.of("Fee head", id));
	}

	public FeeHead create(FeeHeadRequest request) {
		String name = request.name().trim();
		if (heads.existsByName(name)) {
			throw new ConflictException("A fee head named " + name + " already exists");
		}
		Instant now = Instant.now(clock);
		FeeHead saved = insert(FeeHead.builder()
				.name(name)
				// A head is created because the school is about to charge it.
				.active(request.active() == null || request.active())
				.createdAt(now)
				.updatedAt(now)
				.build());
		audit.record(AuditAction.FEE_HEAD_CREATED, AUDIT_ENTITY, saved.getId(), null, saved);
		return saved;
	}

	public FeeHead update(String id, FeeHeadRequest request) {
		FeeHead before = get(id);
		String name = request.name().trim();
		if (heads.existsByNameAndIdNot(name, id)) {
			throw new ConflictException("Another fee head is named " + name);
		}
		FeeHead saved = heads.save(before.toBuilder()
				.name(name)
				.active(request.active() == null ? before.isActive() : request.active())
				.updatedAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.FEE_HEAD_UPDATED, AUDIT_ENTITY, id, before, saved);
		return saved;
	}

	// --- for the other services in this module ----------------------------------------------------

	/**
	 * Head id to name for these ids, in one read. Ids that do not exist are simply absent, so a
	 * structure or invoice referring to a head that has since vanished still renders.
	 */
	public Map<String, String> namesOf(Collection<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return Map.of();
		}
		Map<String, String> names = new LinkedHashMap<>();
		heads.findAllByIdIn(ids).forEach(head -> names.put(head.getId(), head.getName()));
		return names;
	}

	/** The heads behind these ids, for validating a structure. */
	public List<FeeHead> findAllByIdIn(Collection<String> ids) {
		return ids == null || ids.isEmpty() ? List.of() : heads.findAllByIdIn(ids);
	}

	private FeeHead insert(FeeHead head) {
		try {
			return heads.insert(head);
		}
		catch (DuplicateKeyException ex) {
			throw new ConflictException("A fee head named " + head.getName() + " already exists", ex);
		}
	}
}

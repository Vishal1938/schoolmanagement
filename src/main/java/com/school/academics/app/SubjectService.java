package com.school.academics.app;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.school.academics.api.SubjectRequest;
import com.school.academics.domain.Subject;
import com.school.academics.infra.SubjectRepository;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.NotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** Subjects. Names and codes are both unique, because report cards are printed from either. */
@Service
public class SubjectService {

	private static final String AUDIT_ENTITY = "Subject";

	private final SubjectRepository subjects;
	private final AuditService audit;
	private final Clock clock;

	public SubjectService(SubjectRepository subjects, AuditService audit, Clock clock) {
		this.subjects = subjects;
		this.audit = audit;
		this.clock = clock;
	}

	/** Every subject, by name. */
	public List<Subject> list() {
		return subjects.findAllByOrderByNameAsc();
	}

	public Subject get(String id) {
		return subjects.findById(id).orElseThrow(() -> new NotFoundException("No subject with id " + id));
	}

	public Subject create(SubjectRequest request) {
		String name = request.name().trim();
		String code = normalizeCode(request.code());
		if (subjects.existsByName(name)) {
			throw new ConflictException("A subject named " + name + " already exists");
		}
		if (subjects.existsByCode(code)) {
			throw new ConflictException("A subject with code " + code + " already exists");
		}
		Instant now = Instant.now(clock);
		Subject saved = insert(Subject.builder()
				.name(name)
				.code(code)
				.createdAt(now)
				.updatedAt(now)
				.build());
		audit.record(AuditAction.SUBJECT_CREATED, AUDIT_ENTITY, saved.getId(), null, saved);
		return saved;
	}

	public Subject update(String id, SubjectRequest request) {
		Subject before = get(id);
		String name = request.name().trim();
		String code = normalizeCode(request.code());
		if (subjects.existsByNameAndIdNot(name, id)) {
			throw new ConflictException("Another subject is named " + name);
		}
		if (subjects.existsByCodeAndIdNot(code, id)) {
			throw new ConflictException("Another subject has code " + code);
		}
		Subject saved = subjects.save(before.toBuilder()
				.name(name)
				.code(code)
				.updatedAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.SUBJECT_UPDATED, AUDIT_ENTITY, id, before, saved);
		return saved;
	}

	/**
	 * Which of these ids do not exist. Used by {@code SchoolClassService} so a class cannot be given a
	 * subject that was never created, or one that was typed by hand.
	 */
	public Set<String> findMissingIds(Collection<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return Set.of();
		}
		Set<String> found = subjects.findAllByIdIn(ids).stream()
				.map(Subject::getId)
				.collect(Collectors.toSet());
		// Insertion order, so the error message lists them in the order the client sent them.
		return ids.stream()
				.filter(id -> !found.contains(id))
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/** Codes are stored upper-case, so {@code eng} and {@code ENG} cannot both exist. */
	private static String normalizeCode(String code) {
		return code.trim().toUpperCase(Locale.ROOT);
	}

	private Subject insert(Subject subject) {
		try {
			return subjects.insert(subject);
		}
		catch (DuplicateKeyException ex) {
			throw new ConflictException("A subject with that name or code already exists", ex);
		}
	}
}

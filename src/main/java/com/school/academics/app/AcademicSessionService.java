package com.school.academics.app;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.school.academics.api.SessionRequest;
import com.school.academics.domain.AcademicSession;
import com.school.academics.infra.AcademicSessionRepository;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ValidationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Academic sessions, and the rule that exactly one of them is active.
 *
 * <p>Other modules do not call this to find out which year it is — they call {@link AcademicContext},
 * which is the narrow read side of the same data.
 */
@Service
public class AcademicSessionService {

	private static final String AUDIT_ENTITY = "AcademicSession";

	private final AcademicSessionRepository sessions;
	private final AuditService audit;
	private final Clock clock;

	public AcademicSessionService(AcademicSessionRepository sessions, AuditService audit, Clock clock) {
		this.sessions = sessions;
		this.audit = audit;
		this.clock = clock;
	}

	/** Every session, newest first. */
	public List<AcademicSession> list() {
		return sessions.findAllByOrderByStartDateDesc();
	}

	public AcademicSession get(String id) {
		return sessions.findById(id).orElseThrow(() -> new NotFoundException("No session with id " + id));
	}

	/** The current session, if the school has one yet. */
	public Optional<AcademicSession> findActive() {
		return sessions.findFirstByActiveTrue();
	}

	/**
	 * Adds a session. It is created inactive, unless it is the first one this deployment has — a school
	 * with sessions but none of them current would break every module that asks for the current year.
	 */
	public AcademicSession create(SessionRequest request) {
		String name = request.name().trim();
		validateDates(request);
		if (sessions.existsByName(name)) {
			throw new ConflictException("A session named " + name + " already exists");
		}
		boolean first = sessions.count() == 0;
		Instant now = Instant.now(clock);
		AcademicSession session = AcademicSession.builder()
				.name(name)
				.startDate(request.startDate())
				.endDate(request.endDate())
				.active(first)
				.createdAt(now)
				.updatedAt(now)
				.build();
		AcademicSession saved = insert(session);
		audit.record(AuditAction.SESSION_CREATED, AUDIT_ENTITY, saved.getId(), null, saved);
		return saved;
	}

	/**
	 * Makes this session the current one and stands every other one down. Activating the session that
	 * is already active is a no-op rather than an error, so a double-click is harmless.
	 *
	 * <p>The target is flagged first and the others cleared second. Between the two writes two sessions
	 * are briefly active and a reader may see either; the reverse order would leave a window with none
	 * at all, which is the failure that actually breaks callers of {@link AcademicContext}.
	 */
	public AcademicSession activate(String id) {
		AcademicSession before = get(id);
		if (before.isActive()) {
			sessions.deactivateAllExcept(id);
			return before;
		}
		AcademicSession activated = sessions.save(before.toBuilder()
				.active(true)
				.updatedAt(Instant.now(clock))
				.build());
		sessions.deactivateAllExcept(id);
		audit.record(AuditAction.SESSION_ACTIVATED, AUDIT_ENTITY, id, before, activated);
		return activated;
	}

	private void validateDates(SessionRequest request) {
		if (!request.endDate().isAfter(request.startDate())) {
			throw new ValidationException("The session dates are not usable",
					List.of(new FieldViolation("endDate", "must be after startDate")));
		}
	}

	/** The unique index on the name is the real guard; this turns the driver's error into a 409. */
	private AcademicSession insert(AcademicSession session) {
		try {
			return sessions.insert(session);
		}
		catch (DuplicateKeyException ex) {
			throw new ConflictException("A session named " + session.getName() + " already exists", ex);
		}
	}
}

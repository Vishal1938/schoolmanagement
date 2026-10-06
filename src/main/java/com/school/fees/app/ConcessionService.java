package com.school.fees.app;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.school.academics.app.AcademicContext;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ValidationException;
import com.school.fees.api.ConcessionRequest;
import com.school.fees.domain.Concession;
import com.school.fees.domain.ConcessionType;
import com.school.fees.domain.FeeHead;
import com.school.fees.infra.ConcessionRepository;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import org.springframework.stereotype.Service;

/**
 * Concessions: who gets a reduction, on what, and why.
 *
 * <p>Granting one changes nothing that already exists. It is picked up by invoices generated
 * afterwards, and {@code InvoiceService.applyConcession} is the explicit, audited way to pull it back
 * over invoices already raised — so a discount can never silently restate a bill a family has been
 * shown.
 */
@Service
public class ConcessionService {

	static final String AUDIT_ENTITY = "FeeConcession";

	private final ConcessionRepository concessions;
	private final FeeHeadService heads;
	private final StudentService students;
	private final AcademicContext academicContext;
	private final AuditService audit;
	private final Clock clock;

	public ConcessionService(ConcessionRepository concessions, FeeHeadService heads, StudentService students,
			AcademicContext academicContext, AuditService audit, Clock clock) {
		this.concessions = concessions;
		this.heads = heads;
		this.students = students;
		this.academicContext = academicContext;
		this.audit = audit;
		this.clock = clock;
	}

	// --- reading ----------------------------------------------------------------------------------

	/** Concessions of one student, or of the whole session when no student is named. */
	public List<Concession> list(String studentUniqueId) {
		return studentUniqueId == null || studentUniqueId.isBlank()
				? concessions.findBySessionIdOrderByCreatedAtDesc(academicContext.currentSessionId())
				: concessions.findByStudentUniqueIdOrderByCreatedAtDesc(students.requireRef(studentUniqueId).uniqueId());
	}

	public Concession get(String id) {
		return concessions.findById(id).orElseThrow(() -> NotFoundException.of("Concession", id));
	}

	/** What this student is entitled to in this session, oldest first — the input to every calculation. */
	public List<Concession> forStudent(String studentUniqueId, String sessionId) {
		return concessions.findByStudentUniqueIdAndSessionIdOrderByCreatedAtAsc(studentUniqueId, sessionId);
	}

	// --- writing ----------------------------------------------------------------------------------

	public Concession create(ConcessionRequest request) {
		StudentRef student = students.requireRef(request.studentUniqueId());
		String sessionId = academicContext.currentSessionId();
		List<String> headIds = validatedHeadIds(request);
		requireUsableValue(request);

		Concession saved = concessions.insert(Concession.builder()
				.studentUniqueId(student.uniqueId())
				.sessionId(sessionId)
				.type(request.type())
				.value(request.value())
				.headIds(headIds)
				.reason(request.reason().trim())
				.createdAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.FEE_CONCESSION_CREATED, AUDIT_ENTITY, saved.getId(), null, saved);
		return saved;
	}

	/** The student's name, for the response. */
	public String studentNameOf(Concession concession) {
		return students.findRef(concession.getStudentUniqueId()).map(StudentRef::name).orElse(null);
	}

	// --- validation -------------------------------------------------------------------------------

	private void requireUsableValue(ConcessionRequest request) {
		if (request.type() == ConcessionType.PERCENT && request.value() > 100L) {
			throw new ValidationException("A percentage concession cannot exceed 100",
					List.of(new FieldViolation("value", "must be between 1 and 100 for a PERCENT concession")));
		}
	}

	/** Empty stays empty — that is how "every head" is written. Ids that are given must exist. */
	private List<String> validatedHeadIds(ConcessionRequest request) {
		if (request.headIds() == null || request.headIds().isEmpty()) {
			return List.of();
		}
		Set<String> distinct = new LinkedHashSet<>(request.headIds());
		Set<String> found = heads.findAllByIdIn(distinct).stream()
				.map(FeeHead::getId)
				.collect(Collectors.toSet());
		List<FieldViolation> violations = distinct.stream()
				.filter(id -> !found.contains(id))
				.map(id -> new FieldViolation("headIds", "no fee head with id " + id))
				.toList();
		if (!violations.isEmpty()) {
			throw new ValidationException("The concession refers to fee heads that do not exist", violations);
		}
		// Retired heads are allowed here, unlike on a structure: a concession that covers a head the
		// school has since stopped charging is harmless, and it still applies to old invoices.
		return List.copyOf(distinct);
	}
}

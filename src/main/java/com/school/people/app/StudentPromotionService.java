package com.school.people.app;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.school.academics.app.AcademicContext;
import com.school.academics.app.AcademicSessionService;
import com.school.academics.app.SchoolClassService;
import com.school.academics.domain.AcademicSession;
import com.school.academics.domain.SchoolClass;
import com.school.auth.app.UserService;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ValidationException;
import com.school.people.api.PromotionPreview;
import com.school.people.api.PromotionRequest;
import com.school.people.api.PromotionResult;
import com.school.people.domain.Enrollment;
import com.school.people.domain.Student;
import com.school.people.domain.StudentStatus;
import com.school.people.infra.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Moving the whole school up a year.
 *
 * <p>This is the largest write the application ever makes and the only one with no undo, so three
 * things are deliberate about how it works.
 *
 * <p><strong>It is previewed before it is run.</strong> {@code POST …/promote/preview} answers the
 * same question without writing anything, so the admin reads the actual list of students per class,
 * spots the three who are repeating the year, and sends them back as {@code excludeUniqueIds}.
 *
 * <p><strong>It does not change which session is active.</strong> Promotion fills the new session's
 * enrollments; activating it is a separate decision the admin makes in academics once they are
 * happy. Until they do, every other module carries on reading the old year, which is what makes
 * this safe to run early.
 *
 * <p><strong>One transaction per mapping, not one for the whole run.</strong> A single transaction
 * over two thousand students is a long-held lock on the busiest collection in the system and, past
 * MongoDB's sixteen-megabyte oplog entry limit, is not something that reliably commits at all. Each
 * class therefore lands or does not land on its own, and the audit entry for a class is written
 * inside its transaction — so the trail never claims a promotion that rolled back, and never misses
 * one that committed. A run that fails halfway is resumed by sending the remaining mappings again:
 * the classes already moved are no longer in the old session, so they are simply not found a second
 * time.
 */
@Service
public class StudentPromotionService {

	private static final String AUDIT_ENTITY = "StudentPromotion";

	private static final Logger log = LoggerFactory.getLogger(StudentPromotionService.class);

	private final StudentRepository students;
	private final SchoolClassService classes;
	private final AcademicSessionService sessions;
	private final AcademicContext academicContext;
	private final AuditService audit;
	private final TransactionTemplate transactions;
	private final Clock clock;

	public StudentPromotionService(StudentRepository students, SchoolClassService classes,
			AcademicSessionService sessions, AcademicContext academicContext, AuditService audit,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.students = students;
		this.classes = classes;
		this.sessions = sessions;
		this.academicContext = academicContext;
		this.audit = audit;
		// Built here rather than injected: "one transaction per mapping" is a property of this
		// operation, and @Transactional cannot express it from inside the method that loops.
		this.transactions = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	// --- preview ----------------------------------------------------------------------------------

	/**
	 * Who would move, and where to. Writes nothing.
	 *
	 * <p>Only the structure is checked here — the sessions and classes have to resolve for there to
	 * be anything to show. The landing-spot checks are left to {@link #promote}, because they depend
	 * on who is being held back and the preview is what the admin reads in order to decide that.
	 */
	public PromotionPreview preview(PromotionRequest request) {
		Plan plan = plan(request);
		List<PromotionPreview.Mapping> mappings = plan.mappings().stream()
				.map(mapping -> new PromotionPreview.Mapping(
						mapping.from().getId(),
						mapping.from().getName(),
						mapping.graduating() ? null : mapping.to().getId(),
						mapping.graduating() ? null : mapping.to().getName(),
						mapping.graduating(),
						mapping.students().size(),
						mapping.students().stream().map(StudentPromotionService::toPreview).toList()))
				.toList();
		return new PromotionPreview(plan.from().getId(), plan.from().getName(), plan.to().getId(),
				plan.to().getName(), mappings.stream().mapToInt(PromotionPreview.Mapping::studentCount).sum(),
				mappings);
	}

	// --- promotion --------------------------------------------------------------------------------

	/**
	 * Runs the promotion.
	 *
	 * <p>Everything is validated across the whole request first, including the landing spots, so a
	 * run that would leave two students sharing a roll number in 6-A is refused before the first
	 * class moves rather than after the fourth.
	 */
	public PromotionResult promote(PromotionRequest request) {
		Plan plan = plan(request);
		Set<String> excluded = request.excludeUniqueIds().stream()
				.map(UserService::normalizeUniqueId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		validateDestinations(plan, excluded);

		int promoted = 0;
		int heldBack = 0;
		int graduated = 0;
		for (PlannedMapping mapping : plan.mappings()) {
			Counts counts = promoteOneClass(mapping, plan.to(), excluded);
			promoted += counts.promoted();
			heldBack += counts.heldBack();
			graduated += counts.graduated();
		}

		log.info("Promoted {} student(s) into session {}, held back {}, graduated {}",
				promoted, plan.to().getName(), heldBack, graduated);
		return new PromotionResult(promoted, heldBack, graduated);
	}

	/**
	 * One class, in one transaction, with its audit entry written inside it.
	 *
	 * <p>Every student is read, rewritten and saved in one batch rather than one at a time: the
	 * transaction is held for as short a time as the work allows.
	 */
	private Counts promoteOneClass(PlannedMapping mapping, AcademicSession target, Set<String> excluded) {
		return transactions.execute(status -> {
			Instant now = Instant.now(clock);
			List<Student> updated = new ArrayList<>(mapping.students().size());
			int promoted = 0;
			int heldBack = 0;
			int graduated = 0;

			for (Student student : mapping.students()) {
				Enrollment current = student.getEnrollment();
				Student.StudentBuilder builder = student.toBuilder().updatedAt(now);
				if (excluded.contains(student.getUniqueId())) {
					// Held back: the new year, the same class, the same seat.
					builder.enrollmentHistory(appended(student, current))
							.enrollment(new Enrollment(target.getId(), current.classId(), current.section(),
									current.rollNo()));
					heldBack++;
				}
				else if (mapping.graduating()) {
					// No new enrollment, so nothing supersedes the current one and nothing goes into the
					// history. Their final class stays readable where everything else already looks for it.
					builder.status(StudentStatus.ALUMNI);
					graduated++;
				}
				else {
					builder.enrollmentHistory(appended(student, current))
							.enrollment(new Enrollment(target.getId(), mapping.to().getId(), current.section(),
									current.rollNo()));
					promoted++;
				}
				updated.add(builder.build());
			}

			students.saveAll(updated);
			audit.record(AuditAction.STUDENTS_PROMOTED, AUDIT_ENTITY, mapping.from().getId(), null, Map.of(
					"fromClass", mapping.from().getName(),
					"toClass", mapping.graduating() ? "(graduating)" : mapping.to().getName(),
					"toSession", target.getName(),
					"promoted", promoted,
					"heldBack", heldBack,
					"graduated", graduated));
			return new Counts(promoted, heldBack, graduated);
		});
	}

	// --- planning and validation ------------------------------------------------------------------

	/**
	 * Resolves the request into the sessions, classes and students it names, reporting everything
	 * wrong with it at once rather than stopping at the first bad id.
	 */
	private Plan plan(PromotionRequest request) {
		AcademicSession from = academicContext.currentSession();
		List<FieldViolation> violations = new ArrayList<>();
		AcademicSession to = resolveTargetSession(request.toSessionId(), from, violations);

		List<PlannedMapping> mappings = new ArrayList<>();
		Set<String> mappedFrom = new LinkedHashSet<>();
		for (int i = 0; i < request.mappings().size(); i++) {
			PromotionRequest.Mapping mapping = request.mappings().get(i);
			String field = "mappings[" + i + "]";
			SchoolClass source = resolveClass(mapping.fromClassId(), field + ".fromClassId", violations);
			boolean graduating = mapping.toClassId() == null || mapping.toClassId().isBlank();
			SchoolClass target = graduating
					? null
					: resolveClass(mapping.toClassId(), field + ".toClassId", violations);

			if (source == null || (!graduating && target == null)) {
				continue;
			}
			if (!mappedFrom.add(source.getId())) {
				violations.add(new FieldViolation(field + ".fromClassId",
						source.getName() + " is mapped more than once"));
				continue;
			}
			mappings.add(new PlannedMapping(source, target, graduating,
					students.findByEnrollmentSessionIdAndEnrollmentClassIdAndStatusOrderByEnrollmentSectionAscEnrollmentRollNoAsc(
							from.getId(), source.getId(), StudentStatus.ACTIVE)));
		}

		if (!violations.isEmpty()) {
			throw new ValidationException("The promotion could not be planned", violations);
		}
		return new Plan(from, to, mappings);
	}

	/** The session being promoted into: it must exist, and it must not be the one we are leaving. */
	private AcademicSession resolveTargetSession(String id, AcademicSession from, List<FieldViolation> violations) {
		AcademicSession to;
		try {
			to = sessions.get(id);
		}
		catch (NotFoundException ex) {
			violations.add(new FieldViolation("toSessionId", "no session with id " + id));
			return null;
		}
		if (to.getId().equals(from.getId())) {
			violations.add(new FieldViolation("toSessionId", to.getName()
					+ " is the current session — promote into the session that follows it, and activate "
					+ "that session separately once the result looks right"));
		}
		return to;
	}

	private SchoolClass resolveClass(String id, String field, List<FieldViolation> violations) {
		return classes.findById(id).orElseGet(() -> {
			violations.add(new FieldViolation(field, "no class with id " + id));
			return null;
		});
	}

	/**
	 * Checks where every student would land before anything moves.
	 *
	 * <p>Two things can go wrong that the mappings alone do not reveal. A student's section may not
	 * exist on the class they are being moved into — 5-C into a class 6 that only has A and B — and
	 * two students may be sent to the same seat, which happens as soon as two classes are merged into
	 * one, or when somebody held back in 6-A keeps a roll number that an arriving 5-A student also
	 * has. Both would break the uniqueness that attendance and mark sheets rely on, and neither is
	 * fixable afterwards without renumbering a class by hand.
	 *
	 * <p>The new session's existing enrollments are counted as occupied too, so resuming a run that
	 * failed halfway cannot drop a student on top of one that already moved.
	 */
	private void validateDestinations(Plan plan, Set<String> excluded) {
		Set<String> occupied = students.findByEnrollmentSessionId(plan.to().getId()).stream()
				.map(student -> seat(student.getEnrollment()))
				.collect(Collectors.toSet());
		Set<String> claimed = new LinkedHashSet<>();
		Set<String> moving = new LinkedHashSet<>();
		List<FieldViolation> violations = new ArrayList<>();

		for (PlannedMapping mapping : plan.mappings()) {
			for (Student student : mapping.students()) {
				moving.add(student.getUniqueId());
				SchoolClass destination = destinationOf(mapping, student, excluded);
				if (destination == null) {
					continue;
				}
				Enrollment enrollment = student.getEnrollment();
				if (destination.getSections() == null
						|| !destination.getSections().contains(enrollment.section())) {
					violations.add(new FieldViolation(student.getUniqueId(),
							destination.getName() + " has no section " + enrollment.section()));
					continue;
				}
				String seat = seat(destination.getId(), enrollment.section(), enrollment.rollNo());
				if (occupied.contains(seat) || !claimed.add(seat)) {
					violations.add(new FieldViolation(student.getUniqueId(), "roll number "
							+ enrollment.rollNo() + " in " + destination.getName() + "-" + enrollment.section()
							+ " is already taken in " + plan.to().getName()));
				}
			}
		}

		// A mistyped exclusion is silent otherwise: the student it was meant to hold back gets promoted
		// with everybody else, and nobody notices until the register is read out in April.
		for (String uniqueId : excluded) {
			if (!moving.contains(uniqueId)) {
				violations.add(new FieldViolation("excludeUniqueIds",
						uniqueId + " is not an active student in any of the classes being promoted"));
			}
		}

		if (!violations.isEmpty()) {
			throw new ValidationException("The promotion would not leave every student in a usable place",
					violations);
		}
	}

	/** The class this student ends up in, or null when they are graduating rather than moving. */
	private static SchoolClass destinationOf(PlannedMapping mapping, Student student, Set<String> excluded) {
		if (excluded.contains(student.getUniqueId())) {
			// Held back, including out of a graduating class: another year in the same room.
			return mapping.from();
		}
		return mapping.graduating() ? null : mapping.to();
	}

	// --- small helpers ----------------------------------------------------------------------------

	private static String seat(Enrollment enrollment) {
		return enrollment == null ? "" : seat(enrollment.classId(), enrollment.section(), enrollment.rollNo());
	}

	private static String seat(String classId, String section, int rollNo) {
		return classId + "|" + section + "|" + rollNo;
	}

	private static List<Enrollment> appended(Student student, Enrollment current) {
		List<Enrollment> history = new ArrayList<>(
				student.getEnrollmentHistory() == null ? List.of() : student.getEnrollmentHistory());
		history.add(current);
		return List.copyOf(history);
	}

	private static PromotionPreview.Student toPreview(Student student) {
		Enrollment enrollment = student.getEnrollment();
		return new PromotionPreview.Student(student.getUniqueId(), student.getName(),
				enrollment == null ? null : enrollment.section(),
				enrollment == null ? 0 : enrollment.rollNo());
	}

	/** The request, resolved: the two sessions and the classes and students behind each mapping. */
	private record Plan(AcademicSession from, AcademicSession to, List<PlannedMapping> mappings) {
	}

	/** @param to null when {@code graduating}; the students are the ACTIVE ones of {@code from} */
	private record PlannedMapping(SchoolClass from, SchoolClass to, boolean graduating, List<Student> students) {
	}

	private record Counts(int promoted, int heldBack, int graduated) {
	}
}

package com.school.exams.app;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.school.academics.app.AcademicContext;
import com.school.academics.app.SchoolClassService;
import com.school.academics.app.SubjectService;
import com.school.academics.domain.SchoolClass;
import com.school.academics.domain.Subject;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.AppException;
import com.school.common.exceptions.BusinessRuleException;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.ErrorType;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ValidationException;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.CurrentUser;
import com.school.common.security.Permission;
import com.school.exams.api.ExamRequest;
import com.school.exams.api.ExamStatusRequest;
import com.school.exams.domain.Exam;
import com.school.exams.domain.ExamStatus;
import com.school.exams.domain.ExamSubject;
import com.school.exams.infra.ExamRepository;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import org.springframework.stereotype.Service;

/**
 * Exams and the status they are in, which is what gates everything else in this module.
 *
 * <p>The transitions allowed are DRAFT ⇄ MARKS_ENTRY and MARKS_ENTRY ⇄ PUBLISHED. DRAFT straight to
 * PUBLISHED is refused because there is nothing to publish, and the way back from PUBLISHED exists
 * because a mistake found after release has to be fixable.
 */
@Service
public class ExamService {

	static final String AUDIT_ENTITY = "Exam";

	private final ExamRepository exams;
	private final SubjectService subjects;
	private final SchoolClassService classes;
	private final StudentService students;
	private final AcademicContext academicContext;
	private final MarkCompleteness completeness;
	private final AuditService audit;
	private final Clock clock;

	public ExamService(ExamRepository exams, SubjectService subjects, SchoolClassService classes,
			StudentService students, AcademicContext academicContext, MarkCompleteness completeness,
			AuditService audit, Clock clock) {
		this.exams = exams;
		this.subjects = subjects;
		this.classes = classes;
		this.students = students;
		this.academicContext = academicContext;
		this.completeness = completeness;
		this.audit = audit;
		this.clock = clock;
	}

	// --- reading ----------------------------------------------------------------------------------

	/**
	 * Exams of a session, optionally for one class.
	 *
	 * <p>A caller who cannot read student records — in practice a student or a staff member — sees
	 * only PUBLISHED exams, and a student additionally sees only those their own class sits. That is
	 * done here rather than in an annotation because it depends on who is asking, not on what they
	 * are allowed to call.
	 */
	public List<Exam> list(String sessionId, String classId) {
		String session = sessionId != null && !sessionId.isBlank()
				? sessionId
				: academicContext.currentSessionId();
		AuthPrincipal caller = CurrentUser.require();
		boolean staffView = caller.permissions().contains(Permission.STUDENT_READ_BASIC)
				|| caller.permissions().contains(Permission.STUDENT_READ_FULL);

		String effectiveClassId = classId;
		if (!staffView) {
			// Their own class, if they have one. A staff login has no student record and simply sees
			// every published exam, which is harmless — an exam name and a date are not confidential.
			effectiveClassId = students.findRef(caller.uniqueId())
					.map(StudentRef::classId)
					.orElse(classId);
		}

		List<Exam> found = effectiveClassId == null || effectiveClassId.isBlank()
				? exams.findBySessionIdOrderByNameAsc(session)
				: exams.findBySessionIdAndClassIdsContainsOrderByNameAsc(session, effectiveClassId);

		return staffView ? found : found.stream()
				.filter(exam -> exam.getStatus() == ExamStatus.PUBLISHED)
				.toList();
	}

	public Exam get(String id) {
		return exams.findById(id).orElseThrow(() -> NotFoundException.of("Exam", id));
	}

	/** Subject id to name, for whichever subjects these exams mention. */
	public Map<String, String> subjectNames(List<Exam> forExams) {
		Set<String> ids = forExams.stream()
				.flatMap(exam -> exam.getSchedule() == null ? java.util.stream.Stream.<ExamSubject>empty()
						: exam.getSchedule().stream())
				.map(ExamSubject::subjectId)
				.collect(Collectors.toSet());
		return subjects.list().stream()
				.filter(subject -> ids.contains(subject.getId()))
				.collect(Collectors.toMap(Subject::getId, Subject::getName));
	}

	// --- writing ----------------------------------------------------------------------------------

	public Exam create(ExamRequest request) {
		String sessionId = academicContext.currentSessionId();
		String name = request.name().trim();
		List<String> classIds = validatedClassIds(request);
		List<ExamSubject> schedule = validatedSchedule(request);
		if (exams.existsByNameAndSessionId(name, sessionId)) {
			throw new ConflictException("An exam named " + name + " already exists in this session");
		}
		Instant now = Instant.now(clock);
		Exam saved = exams.insert(Exam.builder()
				.name(name)
				.sessionId(sessionId)
				.classIds(classIds)
				.schedule(schedule)
				.status(ExamStatus.DRAFT)
				.createdAt(now)
				.updatedAt(now)
				.build());
		audit.record(AuditAction.EXAM_CREATED, AUDIT_ENTITY, saved.getId(), null, saved);
		return saved;
	}

	/**
	 * Replaces an exam's name, classes and schedule — <strong>only while it is a DRAFT</strong>.
	 * Once marks exist, changing which papers there are or what they are out of would silently
	 * invalidate them.
	 */
	public Exam update(String id, ExamRequest request) {
		Exam before = get(id);
		if (before.getStatus() != ExamStatus.DRAFT) {
			throw new BusinessRuleException("An exam can only be edited while it is a DRAFT. This one is "
					+ before.getStatus() + "; move it back to DRAFT first, which will keep the marks already "
					+ "entered.");
		}
		String name = request.name().trim();
		if (exams.existsByNameAndSessionIdAndIdNot(name, before.getSessionId(), id)) {
			throw new ConflictException("Another exam in this session is named " + name);
		}
		Exam saved = exams.save(before.toBuilder()
				.name(name)
				.classIds(validatedClassIds(request))
				.schedule(validatedSchedule(request))
				.updatedAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.EXAM_UPDATED, AUDIT_ENTITY, id, before, saved);
		return saved;
	}

	/**
	 * Moves an exam to another status.
	 *
	 * <p>Publishing is the one transition with a precondition: every student of every class sitting
	 * the exam must have a mark in every subject. When they do not, this answers 422 with a
	 * {@code missing} array naming each gap, so the office can chase exactly those teachers rather
	 * than being told only that something is incomplete.
	 */
	public Exam changeStatus(String id, ExamStatusRequest request) {
		Exam before = get(id);
		ExamStatus to = request.status();
		if (before.getStatus() == to) {
			return before;
		}
		if (before.getStatus() == ExamStatus.DRAFT && to == ExamStatus.PUBLISHED) {
			throw new BusinessRuleException("A DRAFT exam cannot be published directly. Move it to "
					+ "MARKS_ENTRY, enter the marks, then publish.");
		}
		if (to == ExamStatus.PUBLISHED) {
			requireCompleteMarks(before);
		}
		Exam saved = exams.save(before.toBuilder()
				.status(to)
				.updatedAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.EXAM_STATUS_CHANGED, AUDIT_ENTITY, id, before, saved);
		return saved;
	}

	private void requireCompleteMarks(Exam exam) {
		List<MarkCompleteness.MissingMark> missing = completeness.missingFor(exam);
		if (missing.isEmpty()) {
			return;
		}
		throw new AppException(ErrorType.UNPROCESSABLE,
				"This exam cannot be published: " + missing.size() + " mark(s) have not been entered.",
				Map.of("missing", missing), null);
	}

	// --- validation -------------------------------------------------------------------------------

	private List<String> validatedClassIds(ExamRequest request) {
		List<String> distinct = List.copyOf(new LinkedHashSet<>(request.classIds()));
		List<FieldViolation> violations = new ArrayList<>();
		for (String classId : distinct) {
			if (classes.findById(classId).isEmpty()) {
				violations.add(new FieldViolation("classIds", "no class with id " + classId));
			}
		}
		if (!violations.isEmpty()) {
			throw new ValidationException("The exam refers to classes that do not exist", violations);
		}
		return distinct;
	}

	private List<ExamSubject> validatedSchedule(ExamRequest request) {
		List<FieldViolation> violations = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		List<ExamSubject> schedule = new ArrayList<>();

		List<ExamRequest.Paper> papers = request.schedule();
		for (int i = 0; i < papers.size(); i++) {
			ExamRequest.Paper paper = papers.get(i);
			String field = "schedule[" + i + "]";
			if (!seen.add(paper.subjectId())) {
				violations.add(new FieldViolation(field + ".subjectId", "is listed twice"));
				continue;
			}
			if (paper.passMarks() > paper.maxMarks()) {
				violations.add(new FieldViolation(field + ".passMarks", "must not exceed maxMarks"));
				continue;
			}
			schedule.add(new ExamSubject(paper.subjectId(), paper.date(), paper.maxMarks(), paper.passMarks()));
		}

		Set<String> missingSubjects = subjects.findMissingIds(seen);
		missingSubjects.forEach(id ->
				violations.add(new FieldViolation("schedule", "no subject with id " + id)));

		if (!violations.isEmpty()) {
			throw new ValidationException("The exam schedule is not usable", violations);
		}
		return schedule;
	}

	// --- helpers for the other services in this module --------------------------------------------

	/** The paper for one subject of this exam, or empty when the subject is not in the schedule. */
	public static Optional<ExamSubject> paperFor(Exam exam, String subjectId) {
		return exam.getSchedule() == null ? Optional.empty() : exam.getSchedule().stream()
				.filter(paper -> paper.subjectId().equals(subjectId))
				.findFirst();
	}

	/** The class must be one that sits this exam, and must have the section asked for. */
	public SchoolClass requireClassSitsExam(Exam exam, String classId, String section) {
		if (exam.getClassIds() == null || !exam.getClassIds().contains(classId)) {
			throw new ValidationException("That class does not sit this exam",
					List.of(new FieldViolation("classId", exam.getName() + " is not set for class " + classId)));
		}
		SchoolClass schoolClass = classes.get(classId);
		String normalized = section == null ? null : section.trim().toUpperCase(java.util.Locale.ROOT);
		if (schoolClass.getSections() == null || !schoolClass.getSections().contains(normalized)) {
			throw new ValidationException("That class has no such section",
					List.of(new FieldViolation("section", schoolClass.getName() + " has no section " + normalized)));
		}
		return schoolClass;
	}
}

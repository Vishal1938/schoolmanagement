package com.school.academics.app;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.school.academics.api.ClassAssignmentsRequest;
import com.school.academics.api.ClassRequest;
import com.school.academics.domain.SchoolClass;
import com.school.academics.domain.SectionAssignment;
import com.school.academics.domain.SubjectTeacher;
import com.school.academics.infra.SchoolClassRepository;
import com.school.auth.app.UserService;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ValidationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Classes, their sections and subjects, and who teaches what.
 *
 * <p>Teachers are validated through {@code UserService} — the auth module's public service — rather
 * than against the users collection directly, which is the module-boundary rule in CLAUDE.md. Once
 * B6 lands, that call moves to the employees service and nothing else here changes.
 */
@Service
public class SchoolClassService {

	private static final String AUDIT_ENTITY = "SchoolClass";

	private final SchoolClassRepository classes;
	private final SubjectService subjects;
	private final UserService users;
	private final AuditService audit;
	private final Clock clock;

	public SchoolClassService(SchoolClassRepository classes, SubjectService subjects, UserService users,
			AuditService audit, Clock clock) {
		this.classes = classes;
		this.subjects = subjects;
		this.users = users;
		this.audit = audit;
		this.clock = clock;
	}

	/** Every class, in display order. */
	public List<SchoolClass> list() {
		return classes.findAllInDisplayOrder();
	}

	public SchoolClass get(String id) {
		return findById(id).orElseThrow(() -> new NotFoundException("No class with id " + id));
	}

	/**
	 * The lookup for callers that have a reasonable answer for "it is not there" — resolving a class
	 * name for display, where a deleted class should show as blank rather than fail the whole request.
	 */
	public Optional<SchoolClass> findById(String id) {
		return id == null ? Optional.empty() : classes.findById(id);
	}

	public SchoolClass create(ClassRequest request) {
		String name = request.name().trim();
		List<String> sections = normalizeSections(request.sections());
		List<String> subjectIds = validatedSubjectIds(request.subjectIds());
		if (classes.existsByName(name)) {
			throw new ConflictException("A class named " + name + " already exists");
		}
		Instant now = Instant.now(clock);
		SchoolClass saved = insert(SchoolClass.builder()
				.name(name)
				.order(request.order())
				.sections(sections)
				.subjectIds(subjectIds)
				.assignments(List.of())
				.createdAt(now)
				.updatedAt(now)
				.build());
		audit.record(AuditAction.CLASS_CREATED, AUDIT_ENTITY, saved.getId(), null, saved);
		return saved;
	}

	/**
	 * Replaces the editable fields. Assignments are kept, but pruned to the sections and subjects that
	 * survive the edit: an assignment pointing at a section the class no longer has is unreadable, and
	 * leaving it would quietly resurrect itself if the section were ever added back.
	 */
	public SchoolClass update(String id, ClassRequest request) {
		SchoolClass before = get(id);
		String name = request.name().trim();
		List<String> sections = normalizeSections(request.sections());
		List<String> subjectIds = validatedSubjectIds(request.subjectIds());
		if (classes.existsByNameAndIdNot(name, id)) {
			throw new ConflictException("Another class is named " + name);
		}
		SchoolClass saved = classes.save(before.toBuilder()
				.name(name)
				.order(request.order())
				.sections(sections)
				.subjectIds(subjectIds)
				.assignments(prune(before.getAssignments(), sections, subjectIds))
				.updatedAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.CLASS_UPDATED, AUDIT_ENTITY, id, before, saved);
		return saved;
	}

	/**
	 * Replaces the whole staffing of a class: the class teacher per section, and the teacher per
	 * (section, subject). Everything is checked before anything is written, so a request with one bad
	 * teacher id changes nothing and reports every problem at once.
	 */
	public SchoolClass updateAssignments(String id, ClassAssignmentsRequest request) {
		SchoolClass before = get(id);
		List<SectionAssignment> assignments = validatedAssignments(before, request);
		SchoolClass saved = classes.save(before.toBuilder()
				.assignments(assignments)
				.updatedAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.CLASS_ASSIGNMENTS_UPDATED, AUDIT_ENTITY, id, before, saved);
		return saved;
	}

	// --- validation -------------------------------------------------------------------------------

	private List<SectionAssignment> validatedAssignments(SchoolClass schoolClass, ClassAssignmentsRequest request) {
		List<FieldViolation> violations = new ArrayList<>();
		Set<String> classSections = Set.copyOf(schoolClass.getSections());
		Set<String> classSubjects = Set.copyOf(schoolClass.getSubjectIds());
		Set<String> seenSections = new LinkedHashSet<>();
		List<SectionAssignment> result = new ArrayList<>();

		List<ClassAssignmentsRequest.SectionAssignment> requested = request.assignments();
		for (int i = 0; i < requested.size(); i++) {
			ClassAssignmentsRequest.SectionAssignment entry = requested.get(i);
			String field = "assignments[" + i + "]";
			String section = normalizeSection(entry.section());
			if (!classSections.contains(section)) {
				violations.add(new FieldViolation(field + ".section",
						"class " + schoolClass.getName() + " has no section " + section));
				continue;
			}
			if (!seenSections.add(section)) {
				violations.add(new FieldViolation(field + ".section", "section " + section + " is listed twice"));
				continue;
			}
			String classTeacher = requireTeacher(entry.classTeacher(), field + ".classTeacher", violations);
			result.add(new SectionAssignment(section, classTeacher,
					validatedSubjectTeachers(entry.subjectTeachers(), classSubjects, field, violations)));
		}

		if (!violations.isEmpty()) {
			throw new ValidationException("The assignments could not be applied", violations);
		}
		return result;
	}

	private List<SubjectTeacher> validatedSubjectTeachers(List<ClassAssignmentsRequest.SubjectTeacher> requested,
			Set<String> classSubjects, String parentField, List<FieldViolation> violations) {
		if (requested == null || requested.isEmpty()) {
			return List.of();
		}
		Set<String> seenSubjects = new LinkedHashSet<>();
		List<SubjectTeacher> result = new ArrayList<>();
		for (int i = 0; i < requested.size(); i++) {
			ClassAssignmentsRequest.SubjectTeacher entry = requested.get(i);
			String field = parentField + ".subjectTeachers[" + i + "]";
			if (!classSubjects.contains(entry.subjectId())) {
				violations.add(new FieldViolation(field + ".subjectId", "is not a subject of this class"));
				continue;
			}
			if (!seenSubjects.add(entry.subjectId())) {
				violations.add(new FieldViolation(field + ".subjectId", "is listed twice for this section"));
				continue;
			}
			String teacher = requireTeacher(entry.teacher(), field + ".teacher", violations);
			if (teacher != null) {
				result.add(new SubjectTeacher(entry.subjectId(), teacher));
			}
		}
		return result;
	}

	/**
	 * @return the normalized uniqueId, or null when none was given — a section may legitimately have no
	 *         class teacher yet. An id that is given but is not an enabled teacher is a violation.
	 */
	private String requireTeacher(String uniqueId, String field, List<FieldViolation> violations) {
		if (uniqueId == null || uniqueId.isBlank()) {
			return null;
		}
		String normalized = UserService.normalizeUniqueId(uniqueId);
		if (!users.isTeacher(normalized)) {
			violations.add(new FieldViolation(field, normalized + " is not an active teacher"));
			return null;
		}
		return normalized;
	}

	/** Upper-case, de-duplicated, in the order given. */
	private static List<String> normalizeSections(List<String> sections) {
		Set<String> normalized = new LinkedHashSet<>();
		List<FieldViolation> violations = new ArrayList<>();
		for (int i = 0; i < sections.size(); i++) {
			if (!normalized.add(normalizeSection(sections.get(i)))) {
				violations.add(new FieldViolation("sections[" + i + "]", "duplicates an earlier section"));
			}
		}
		if (!violations.isEmpty()) {
			throw new ValidationException("The sections could not be saved", violations);
		}
		return List.copyOf(normalized);
	}

	private static String normalizeSection(String section) {
		return section.trim().toUpperCase(Locale.ROOT);
	}

	private List<String> validatedSubjectIds(List<String> subjectIds) {
		if (subjectIds == null || subjectIds.isEmpty()) {
			return List.of();
		}
		List<String> distinct = List.copyOf(new LinkedHashSet<>(subjectIds));
		Set<String> missing = subjects.findMissingIds(distinct);
		if (!missing.isEmpty()) {
			throw new ValidationException("The class refers to subjects that do not exist",
					missing.stream().map(id -> new FieldViolation("subjectIds", "no subject with id " + id)).toList());
		}
		return distinct;
	}

	/** Drops assignments to sections or subjects the class no longer has. */
	private static List<SectionAssignment> prune(List<SectionAssignment> assignments, List<String> sections,
			List<String> subjectIds) {
		if (assignments == null || assignments.isEmpty()) {
			return List.of();
		}
		Set<String> keptSections = Set.copyOf(sections);
		Set<String> keptSubjects = Set.copyOf(subjectIds);
		return assignments.stream()
				.filter(assignment -> keptSections.contains(assignment.section()))
				.map(assignment -> new SectionAssignment(assignment.section(), assignment.classTeacherUniqueId(),
						assignment.subjectTeachers() == null ? List.of() : assignment.subjectTeachers().stream()
								.filter(st -> keptSubjects.contains(st.subjectId()))
								.toList()))
				.toList();
	}

	private SchoolClass insert(SchoolClass schoolClass) {
		try {
			return classes.insert(schoolClass);
		}
		catch (DuplicateKeyException ex) {
			throw new ConflictException("A class named " + schoolClass.getName() + " already exists", ex);
		}
	}
}

package com.school.exams.app;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.school.academics.app.SubjectService;
import com.school.academics.domain.SchoolClass;
import com.school.auth.app.UserService;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.BusinessRuleException;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ValidationException;
import com.school.common.security.CurrentUser;
import com.school.exams.api.MarksEntryRequest;
import com.school.exams.api.MarksEntryResponse;
import com.school.exams.domain.Exam;
import com.school.exams.domain.ExamStatus;
import com.school.exams.domain.ExamSubject;
import com.school.exams.domain.Mark;
import com.school.exams.infra.MarkRepository;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import org.springframework.stereotype.Service;

/**
 * Entering marks: one subject, one class-section, one grid at a time.
 *
 * <p>Any teacher may enter marks for any class, as with attendance — cover and shared marking are
 * normal. What is enforced is <em>when</em>: only while the exam is in MARKS_ENTRY. A DRAFT has no
 * fixed schedule to mark against, and a PUBLISHED exam has results already in students' hands.
 */
@Service
public class MarksService {

	private static final String AUDIT_ENTITY = "Marks";

	private final MarkRepository marks;
	private final ExamService exams;
	private final StudentService students;
	private final SubjectService subjects;
	private final AuditService audit;
	private final Clock clock;

	public MarksService(MarkRepository marks, ExamService exams, StudentService students, SubjectService subjects,
			AuditService audit, Clock clock) {
		this.marks = marks;
		this.exams = exams;
		this.students = students;
		this.subjects = subjects;
		this.audit = audit;
		this.clock = clock;
	}

	/** The grid: every ACTIVE student of the class-section in roll order, with their mark or null. */
	public MarksEntryResponse grid(String examId, String classId, String section, String subjectId) {
		Exam exam = exams.get(examId);
		SchoolClass schoolClass = exams.requireClassSitsExam(exam, classId, section);
		ExamSubject paper = requirePaper(exam, subjectId);
		String normalizedSection = normalizeSection(section);

		List<StudentRef> roll = students.activeInSection(classId, normalizedSection);
		Map<String, Mark> byStudent = marks.findByExamIdAndSubjectIdAndStudentUniqueIdIn(examId, subjectId,
						roll.stream().map(StudentRef::uniqueId).toList()).stream()
				.collect(Collectors.toMap(Mark::getStudentUniqueId, mark -> mark, (first, second) -> first));

		List<MarksEntryResponse.Row> rows = roll.stream()
				.map(student -> {
					Mark mark = byStudent.get(student.uniqueId());
					return new MarksEntryResponse.Row(student.uniqueId(), student.name(), student.rollNo(),
							mark == null ? null : mark.getMarksObtained(),
							mark != null && mark.isAbsent(),
							mark == null ? null : mark.getRemarks(),
							mark == null ? null : mark.getEnteredBy());
				})
				.toList();

		boolean editable = exam.getStatus() == ExamStatus.MARKS_ENTRY;
		return new MarksEntryResponse(examId, exam.getName(), exam.getStatus(), editable,
				editable ? null : lockedReason(exam.getStatus()),
				classId, schoolClass.getName(), normalizedSection,
				subjectId, subjectNameOf(subjectId), paper.maxMarks(), paper.passMarks(), rows);
	}

	/**
	 * Saves a grid.
	 *
	 * <p>An upsert per row, not a replace: a student left out keeps whatever mark they had, so a
	 * teacher entering half a class does not wipe the other half. Everything is validated first, so a
	 * single out-of-range mark fails the request and writes nothing.
	 */
	public MarksEntryResponse save(String examId, String classId, String section, String subjectId,
			MarksEntryRequest request) {
		Exam exam = exams.get(examId);
		exams.requireClassSitsExam(exam, classId, section);
		ExamSubject paper = requirePaper(exam, subjectId);
		if (exam.getStatus() != ExamStatus.MARKS_ENTRY) {
			throw new BusinessRuleException(lockedReason(exam.getStatus()));
		}
		String normalizedSection = normalizeSection(section);

		Set<String> roll = students.activeInSection(classId, normalizedSection).stream()
				.map(StudentRef::uniqueId)
				.collect(Collectors.toSet());
		List<MarksEntryRequest.Entry> validated = validate(request, roll, paper);

		String enteredBy = CurrentUser.require().uniqueId();
		Instant now = Instant.now(clock);
		List<Mark> before = new ArrayList<>();
		List<Mark> after = new ArrayList<>();

		for (MarksEntryRequest.Entry entry : validated) {
			String studentUniqueId = UserService.normalizeUniqueId(entry.studentUniqueId());
			Mark existing = marks.findByExamIdAndStudentUniqueIdAndSubjectId(examId, studentUniqueId, subjectId)
					.orElse(null);
			// An absent student has no mark, whatever the client sent alongside the flag.
			Integer obtained = entry.absent() ? null : entry.marksObtained();
			Mark toSave = existing == null
					? Mark.builder()
							.examId(examId)
							.studentUniqueId(studentUniqueId)
							.subjectId(subjectId)
							.marksObtained(obtained)
							.absent(entry.absent())
							.remarks(trimToNull(entry.remarks()))
							.enteredBy(enteredBy)
							.enteredAt(now)
							.createdAt(now)
							.build()
					: existing.toBuilder()
							.marksObtained(obtained)
							.absent(entry.absent())
							.remarks(trimToNull(entry.remarks()))
							.enteredBy(enteredBy)
							.enteredAt(now)
							.build();
			if (existing != null) {
				before.add(existing);
			}
			after.add(marks.save(toSave));
		}

		audit.record(AuditAction.MARKS_ENTERED, AUDIT_ENTITY,
				examId + "/" + classId + "/" + normalizedSection + "/" + subjectId, before, after);
		return grid(examId, classId, normalizedSection, subjectId);
	}

	// --- internals --------------------------------------------------------------------------------

	/** Rows must name students on this roll, once each, with a mark inside the paper's range. */
	private List<MarksEntryRequest.Entry> validate(MarksEntryRequest request, Set<String> roll, ExamSubject paper) {
		List<FieldViolation> violations = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		List<MarksEntryRequest.Entry> accepted = new ArrayList<>();

		List<MarksEntryRequest.Entry> entries = request.entries();
		for (int i = 0; i < entries.size(); i++) {
			MarksEntryRequest.Entry entry = entries.get(i);
			String studentUniqueId = UserService.normalizeUniqueId(entry.studentUniqueId());
			String field = "entries[" + i + "]";
			if (!roll.contains(studentUniqueId)) {
				violations.add(new FieldViolation(field + ".studentUniqueId",
						studentUniqueId + " is not an active student of this class-section"));
				continue;
			}
			if (!seen.add(studentUniqueId)) {
				violations.add(new FieldViolation(field + ".studentUniqueId", studentUniqueId + " is listed twice"));
				continue;
			}
			Integer obtained = entry.marksObtained();
			if (!entry.absent() && obtained != null && obtained > paper.maxMarks()) {
				violations.add(new FieldViolation(field + ".marksObtained",
						"must not exceed the paper's maxMarks of " + paper.maxMarks()));
				continue;
			}
			accepted.add(entry);
		}

		if (!violations.isEmpty()) {
			throw new ValidationException("The marks could not be saved", violations);
		}
		return accepted;
	}

	private ExamSubject requirePaper(Exam exam, String subjectId) {
		return ExamService.paperFor(exam, subjectId)
				.orElseThrow(() -> new ValidationException("That subject is not part of this exam",
						List.of(new FieldViolation("subjectId",
								exam.getName() + " has no paper for subject " + subjectId))));
	}

	private String subjectNameOf(String subjectId) {
		return subjects.list().stream()
				.filter(subject -> subject.getId().equals(subjectId))
				.map(com.school.academics.domain.Subject::getName)
				.findFirst()
				.orElse(null);
	}

	private static String lockedReason(ExamStatus status) {
		return status == ExamStatus.DRAFT
				? "Marks cannot be entered while the exam is a DRAFT. Move it to MARKS_ENTRY first."
				: "This exam is PUBLISHED and its marks are locked. Move it back to MARKS_ENTRY to correct them.";
	}

	private static String normalizeSection(String section) {
		return section == null ? null : section.trim().toUpperCase(java.util.Locale.ROOT);
	}

	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

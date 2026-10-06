package com.school.attendance.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.school.academics.app.SchoolClassService;
import com.school.academics.domain.SchoolClass;
import com.school.attendance.api.AttendanceHistoryResponse;
import com.school.attendance.api.AttendanceSummary;
import com.school.attendance.api.ClassRegisterResponse;
import com.school.attendance.api.MarkStudentAttendanceRequest;
import com.school.attendance.domain.AttendanceStatus;
import com.school.attendance.domain.StudentAttendance;
import com.school.attendance.domain.StudentAttendanceEntry;
import com.school.attendance.infra.StudentAttendanceRepository;
import com.school.auth.app.UserService;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.BusinessRuleException;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ForbiddenException;
import com.school.common.exceptions.ValidationException;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.CurrentUser;
import com.school.common.security.Permission;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Student attendance: one register per class-section per day.
 *
 * <p>Any teacher may mark any class — schools swap cover around constantly and a rule that only the
 * class teacher may submit means the register goes unmarked the day they are off. What is enforced
 * instead is <em>when</em>: {@link AttendanceCalendar} holds that, and the office can always fix a
 * mistake afterwards.
 */
@Service
public class StudentAttendanceService {

	private static final String AUDIT_ENTITY = "StudentAttendance";

	private final StudentAttendanceRepository attendance;
	private final SchoolClassService classes;
	private final StudentService students;
	private final AttendanceCalendar calendar;
	private final AuditService audit;
	private final Clock clock;

	public StudentAttendanceService(StudentAttendanceRepository attendance, SchoolClassService classes,
			StudentService students, AttendanceCalendar calendar, AuditService audit, Clock clock) {
		this.attendance = attendance;
		this.classes = classes;
		this.students = students;
		this.calendar = calendar;
		this.audit = audit;
		this.clock = clock;
	}

	// --- the daily register -----------------------------------------------------------------------

	/**
	 * The register for one class-section on one day: the current roll, in roll order, each with their
	 * mark or null.
	 *
	 * <p>Rows come from the roll rather than from the stored document, so a student admitted since
	 * the day was marked shows up unmarked instead of being invisible.
	 */
	public ClassRegisterResponse register(String classId, String section, LocalDate date) {
		SchoolClass schoolClass = requireSection(classId, section);
		String normalizedSection = normalizeSection(section);
		Optional<StudentAttendance> stored = attendance.findByClassIdAndSectionAndDate(classId, normalizedSection, date);
		Map<String, AttendanceStatus> marks = stored
				.map(StudentAttendanceService::marksByStudent)
				.orElseGet(Map::of);
		Markability markability = calendar.check(date, stored.map(StudentAttendance::getMarkedAt).orElse(null));

		List<ClassRegisterResponse.Row> rows = students.activeInSection(classId, normalizedSection).stream()
				.map(student -> new ClassRegisterResponse.Row(student.uniqueId(), student.name(), student.rollNo(),
						marks.get(student.uniqueId())))
				.toList();

		return new ClassRegisterResponse(classId, schoolClass.getName(), normalizedSection, date,
				stored.isPresent(), markability.allowed(), markability.reason(),
				stored.map(StudentAttendance::getMarkedBy).orElse(null),
				stored.map(StudentAttendance::getMarkedAt).orElse(null),
				rows);
	}

	/**
	 * Submits or corrects the whole register for a day.
	 *
	 * <p>Everything is validated before anything is written: an entry for somebody who is not on this
	 * roll, or the same student twice, fails the request and changes nothing.
	 */
	public ClassRegisterResponse mark(String classId, String section, LocalDate date,
			MarkStudentAttendanceRequest request) {
		requireSection(classId, section);
		String normalizedSection = normalizeSection(section);
		Optional<StudentAttendance> existing =
				attendance.findByClassIdAndSectionAndDate(classId, normalizedSection, date);

		Markability markability = calendar.check(date, existing.map(StudentAttendance::getMarkedAt).orElse(null));
		if (!markability.allowed()) {
			throw new BusinessRuleException(markability.reason());
		}

		List<StudentAttendanceEntry> entries = validatedEntries(classId, normalizedSection, request);
		AuthPrincipal caller = CurrentUser.require();
		Instant now = Instant.now(clock);

		StudentAttendance before = existing.orElse(null);
		StudentAttendance toSave = existing
				.map(found -> found.toBuilder().entries(entries).markedBy(caller.uniqueId()).markedAt(now).build())
				.orElseGet(() -> StudentAttendance.builder()
						.classId(classId)
						.section(normalizedSection)
						.date(date)
						.entries(entries)
						.markedBy(caller.uniqueId())
						.markedAt(now)
						.createdAt(now)
						.build());

		StudentAttendance saved = save(toSave);
		audit.record(AuditAction.STUDENT_ATTENDANCE_MARKED, AUDIT_ENTITY, auditId(classId, normalizedSection, date),
				before, saved);
		return register(classId, normalizedSection, date);
	}

	// --- one student's history --------------------------------------------------------------------

	/**
	 * One student's marked days in a range, with the totals.
	 *
	 * <p>An admin or a teacher may read any student's; a student may read only their own. That check
	 * is here rather than in an annotation because it depends on which student was asked for.
	 */
	public AttendanceHistoryResponse studentHistory(String uniqueId, LocalDate from, LocalDate to) {
		requireRange(from, to);
		AuthPrincipal caller = CurrentUser.require();
		StudentRef student = students.requireRef(uniqueId);
		boolean mayReadAnyone = caller.permissions().contains(Permission.STUDENT_READ_BASIC)
				|| caller.permissions().contains(Permission.STUDENT_READ_FULL);
		if (!mayReadAnyone && !student.uniqueId().equals(caller.uniqueId())) {
			throw new ForbiddenException("You may only read your own attendance");
		}

		List<AttendanceHistoryResponse.Day> days = days(student.uniqueId(), from, to);
		return new AttendanceHistoryResponse(student.uniqueId(), student.name(), from, to, days,
				AttendanceSummary.of(statuses(days)));
	}

	/**
	 * One student's attendance over a range, reduced to what a report card prints (B9).
	 *
	 * <p>No caller check here, unlike {@link #studentHistory}: this is reached from the exams module,
	 * which has already decided whether the caller may see this student's report card at all. It
	 * deliberately cannot return anything finer than a percentage, so nothing more can leak if that
	 * decision is ever made somewhere new.
	 */
	public AttendanceShare shareFor(String studentUniqueId, LocalDate from, LocalDate to) {
		if (from == null || to == null || to.isBefore(from)) {
			return AttendanceShare.none();
		}
		AttendanceSummary summary = AttendanceSummary.of(
				statuses(days(UserService.normalizeUniqueId(studentUniqueId), from, to)));
		return new AttendanceShare(summary.workingDays(), summary.percentage());
	}

	/** The days of this student's attendance that were actually marked in the range, in date order. */
	private List<AttendanceHistoryResponse.Day> days(String studentUniqueId, LocalDate from, LocalDate to) {
		return attendance.findByEntriesStudentUniqueIdAndDateBetweenOrderByDateAsc(studentUniqueId, from, to)
				.stream()
				.flatMap(register -> register.getEntries().stream()
						.filter(entry -> studentUniqueId.equals(entry.studentUniqueId()))
						.map(entry -> new AttendanceHistoryResponse.Day(register.getDate(), entry.status())))
				.toList();
	}

	private static List<AttendanceStatus> statuses(List<AttendanceHistoryResponse.Day> days) {
		return days.stream().map(AttendanceHistoryResponse.Day::status).toList();
	}

	// --- internals --------------------------------------------------------------------------------

	/** The class must exist and must actually have this section. */
	private SchoolClass requireSection(String classId, String section) {
		SchoolClass schoolClass = classes.get(classId);
		String normalized = normalizeSection(section);
		if (schoolClass.getSections() == null || !schoolClass.getSections().contains(normalized)) {
			throw new ValidationException("That class has no such section",
					List.of(new FieldViolation("section", schoolClass.getName() + " has no section " + normalized)));
		}
		return schoolClass;
	}

	/** Entries must name students actually on this roll, once each. */
	private List<StudentAttendanceEntry> validatedEntries(String classId, String section,
			MarkStudentAttendanceRequest request) {
		Set<String> roll = students.activeInSection(classId, section).stream()
				.map(StudentRef::uniqueId)
				.collect(Collectors.toSet());
		List<FieldViolation> violations = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		List<StudentAttendanceEntry> entries = new ArrayList<>();

		List<MarkStudentAttendanceRequest.Entry> requested = request.entries();
		for (int i = 0; i < requested.size(); i++) {
			MarkStudentAttendanceRequest.Entry entry = requested.get(i);
			String studentUniqueId = UserService.normalizeUniqueId(entry.studentUniqueId());
			String field = "entries[" + i + "].studentUniqueId";
			if (!roll.contains(studentUniqueId)) {
				violations.add(new FieldViolation(field,
						studentUniqueId + " is not an active student of this class-section"));
				continue;
			}
			if (!seen.add(studentUniqueId)) {
				violations.add(new FieldViolation(field, studentUniqueId + " is listed twice"));
				continue;
			}
			entries.add(new StudentAttendanceEntry(studentUniqueId, entry.status()));
		}

		if (!violations.isEmpty()) {
			throw new ValidationException("The register could not be saved", violations);
		}
		return entries;
	}

	/**
	 * The unique index on (classId, section, date) is the real guard against two teachers submitting
	 * the same register at once; this turns the loser's error into a 409.
	 */
	private StudentAttendance save(StudentAttendance register) {
		try {
			return attendance.save(register);
		}
		catch (DuplicateKeyException ex) {
			throw new ConflictException(
					"This register was submitted by somebody else a moment ago. Reload and try again.", ex);
		}
	}

	private static Map<String, AttendanceStatus> marksByStudent(StudentAttendance register) {
		return register.getEntries() == null ? Map.of() : register.getEntries().stream()
				.collect(Collectors.toMap(StudentAttendanceEntry::studentUniqueId, StudentAttendanceEntry::status,
						(first, second) -> first));
	}

	/** Readable in the audit trail, where the Mongo id of a register means nothing to anybody. */
	private static String auditId(String classId, String section, LocalDate date) {
		return classId + "/" + section + "/" + date;
	}

	private static String normalizeSection(String section) {
		return section == null ? null : section.trim().toUpperCase(Locale.ROOT);
	}

	static void requireRange(LocalDate from, LocalDate to) {
		if (from == null || to == null) {
			throw new ValidationException("A date range is required",
					List.of(new FieldViolation("from", "both from and to are required")));
		}
		if (to.isBefore(from)) {
			throw new ValidationException("The date range is not usable",
					List.of(new FieldViolation("to", "must not be before from")));
		}
	}

}

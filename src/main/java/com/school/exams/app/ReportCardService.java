package com.school.exams.app;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.school.academics.app.AcademicSessionService;
import com.school.academics.domain.AcademicSession;
import com.school.academics.domain.SchoolClass;
import com.school.academics.domain.SectionAssignment;
import com.school.attendance.app.AttendanceShare;
import com.school.attendance.app.StudentAttendanceService;
import com.school.people.app.EmployeeRef;
import com.school.people.app.EmployeeService;
import com.school.people.app.StudentRef;
import com.school.schoolconfig.app.SchoolConfigService;
import com.school.schoolconfig.domain.Principal;
import org.springframework.stereotype.Service;

/**
 * The report card PDF: who may have one, and what goes on it.
 *
 * <p>{@link ResultService#forStudentExam} decides both of the questions that can go wrong — whether
 * this caller may read this student, and whether the exam is PUBLISHED — so this class is about
 * gathering the rest: the school's identity and address, the session's attendance, the class
 * teacher, and the footer. {@link ReportCardPdf} then draws it and reads nothing of its own.
 */
@Service
public class ReportCardService {

	private final ResultService results;
	private final AcademicSessionService sessions;
	private final StudentAttendanceService attendance;
	private final EmployeeService employees;
	private final SchoolConfigService schoolConfig;
	private final ReportCardPdf pdf;
	private final Clock clock;

	public ReportCardService(ResultService results, AcademicSessionService sessions,
			StudentAttendanceService attendance, EmployeeService employees, SchoolConfigService schoolConfig,
			ReportCardPdf pdf, Clock clock) {
		this.results = results;
		this.sessions = sessions;
		this.attendance = attendance;
		this.employees = employees;
		this.schoolConfig = schoolConfig;
		this.pdf = pdf;
		this.clock = clock;
	}

	/** One student's report card for one PUBLISHED exam. */
	public ReportCard forStudent(String uniqueId, String examId) {
		ResultService.StudentExamResult result = results.forStudentExam(uniqueId, examId);
		StudentRef student = result.student();
		AcademicSession session = sessions.get(result.exam().getSessionId());
		Principal principal = schoolConfig.principal();

		ReportCardData data = new ReportCardData(
				schoolConfig.identity(),
				schoolConfig.contact(),
				session.getName(),
				result.schoolClass().getName(),
				student,
				result.result(),
				attendanceFor(student, session),
				classTeacherName(result.schoolClass(), student.section()),
				principal == null ? null : principal.name(),
				principal == null ? null : principal.designation(),
				schoolConfig.reportCardFooter(),
				LocalDate.now(clock));

		return new ReportCard(fileName(student, result.exam().getName()), pdf.render(data));
	}

	/**
	 * The student's attendance across the whole session, not only up to the exam. That is what a
	 * parent reads the line as, and it is also the only figure that stays the same whichever exam's
	 * card is printed.
	 */
	private AttendanceShare attendanceFor(StudentRef student, AcademicSession session) {
		return attendance.shareFor(student.uniqueId(), session.getStartDate(), session.getEndDate());
	}

	/** The name of whoever is expected to sign as class teacher, or null when the section has none. */
	private String classTeacherName(SchoolClass schoolClass, String section) {
		List<SectionAssignment> assignments = schoolClass.getAssignments() == null
				? List.of()
				: schoolClass.getAssignments();
		return assignments.stream()
				.filter(assignment -> assignment.section().equals(section))
				.map(SectionAssignment::classTeacherUniqueId)
				.filter(Objects::nonNull)
				.findFirst()
				.flatMap(employees::findRef)
				.map(EmployeeRef::name)
				.orElse(null);
	}

	/** {@code report-card-DEMO-STU-26-00007-half-yearly.pdf}. */
	private static String fileName(StudentRef student, String examName) {
		String exam = examName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
		return "report-card-" + student.uniqueId() + (exam.isEmpty() ? "" : "-" + exam) + ".pdf";
	}
}

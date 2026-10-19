package com.school.dashboard.app;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import com.school.academics.app.AcademicContext;
import com.school.academics.app.SchoolClassService;
import com.school.academics.domain.AcademicSession;
import com.school.academics.domain.SchoolClass;
import com.school.attendance.app.StudentAttendanceService;
import com.school.dashboard.api.AdminDashboardResponse;
import com.school.exams.app.ExamService;
import com.school.fees.app.FeeDashboard;
import com.school.fees.app.FeeDashboardService;
import com.school.notice.app.NoticeService;
import com.school.payment.app.CollectionReportService;
import com.school.payment.app.PaymentService;
import com.school.payroll.app.PayrollService;
import com.school.people.app.EmployeeRef;
import com.school.people.app.EmployeeService;
import com.school.people.app.StudentService;
import com.school.quiz.app.QuizService;
import com.school.schoolconfig.app.ModuleGate;
import com.school.schoolconfig.domain.SchoolModule;
import org.springframework.stereotype.Service;

/**
 * The admin dashboard: one read-only aggregate assembled from every other module's public service.
 *
 * <p>This module owns no collection and writes nothing. It is the one place in the application that
 * legitimately reaches into nine others, which is exactly why it goes through their {@code app}
 * services rather than their repositories — the figures here have to mean the same thing they mean
 * on the screen each one backs, and they do because they come from the same code.
 *
 * <p><strong>Modules that are off are omitted rather than failed.</strong> {@link ModuleGate#require}
 * is the right behaviour for an endpoint that belongs to a module; it is the wrong behaviour here,
 * because a college with exams switched off still wants its fees dashboard. So each optional section
 * is asked for only if its module is on, and left null otherwise — which Jackson drops from the body
 * entirely. The gated sections are the only ones that can disappear: fees, payments and payroll are
 * not switchable, because every deployment takes money and pays people.
 *
 * <p>Deliberately uncached. B16 suggests a 60-second Caffeine cache, and this is cheap enough not to
 * need one yet — roughly a dozen reads, all index-served — while a cache would mean an administrator
 * who records a payment and returns to the dashboard sees the figure from before it.
 */
@Service
public class AdminDashboardService {

	private final ModuleGate modules;
	private final AcademicContext academicContext;
	private final SchoolClassService classes;
	private final StudentService students;
	private final EmployeeService employees;
	private final StudentAttendanceService attendance;
	private final FeeDashboardService fees;
	private final CollectionReportService collections;
	private final PaymentService payments;
	private final PayrollService payroll;
	private final ExamService exams;
	private final NoticeService notices;
	private final QuizService quizzes;
	private final Clock clock;

	public AdminDashboardService(ModuleGate modules, AcademicContext academicContext, SchoolClassService classes,
			StudentService students, EmployeeService employees, StudentAttendanceService attendance,
			FeeDashboardService fees, CollectionReportService collections, PaymentService payments,
			PayrollService payroll, ExamService exams, NoticeService notices, QuizService quizzes, Clock clock) {
		this.modules = modules;
		this.academicContext = academicContext;
		this.classes = classes;
		this.students = students;
		this.employees = employees;
		this.attendance = attendance;
		this.fees = fees;
		this.collections = collections;
		this.payments = payments;
		this.payroll = payroll;
		this.exams = exams;
		this.notices = notices;
		this.quizzes = quizzes;
		this.clock = clock;
	}

	/** Everything the admin dashboard shows. */
	public AdminDashboardResponse snapshot() {
		LocalDate today = LocalDate.now(clock);
		FeeDashboard feeFigures = fees.snapshot();

		return new AdminDashboardResponse(
				today,
				session(),
				counts(),
				modules.isEnabled(SchoolModule.STUDENT_ATTENDANCE) ? attendance.day(today) : null,
				collections.dayTotal(today),
				feeFigures.session(),
				feeFigures.month(),
				feeFigures.monthByClass(),
				feeFigures.pendingOneTime(),
				payroll.pendingThisMonth(),
				payments.recent(),
				modules.isEnabled(SchoolModule.EXAMS) ? exams.overview() : null,
				modules.isEnabled(SchoolModule.NOTICES)
						? new AdminDashboardResponse.Notices(notices.activeCount())
						: null,
				modules.isEnabled(SchoolModule.QUIZZES) ? quizzes.overview() : null);
	}

	/** The active session, or null on a deployment that has none yet. */
	private AdminDashboardResponse.Session session() {
		if (!academicContext.hasCurrentSession()) {
			return null;
		}
		AcademicSession active = academicContext.currentSession();
		return new AdminDashboardResponse.Session(active.getId(), active.getName());
	}

	/**
	 * Head counts.
	 *
	 * <p>Students are counted against the active session, so last year's leavers are not in the
	 * figure; with no session there is no roll, and zero is the honest answer. Employees are split on
	 * {@link EmployeeRef#employeeType()}, which is a string precisely so this module does not have to
	 * see the people module's enum.
	 */
	private AdminDashboardResponse.Counts counts() {
		List<EmployeeRef> active = employees.activeEmployees();
		int teachers = (int) active.stream().filter(employee -> "TEACHER".equals(employee.employeeType())).count();
		long roll = academicContext.hasCurrentSession()
				? students.activeCount(academicContext.currentSessionId())
				: 0L;
		List<SchoolClass> all = classes.list();
		int sections = all.stream()
				.mapToInt(schoolClass -> schoolClass.getSections() == null ? 0 : schoolClass.getSections().size())
				.sum();
		return new AdminDashboardResponse.Counts((int) roll, teachers, active.size() - teachers, all.size(),
				sections);
	}
}

package com.school.demodata.app;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.school.academics.app.AcademicContext;
import com.school.academics.app.SchoolClassService;
import com.school.academics.domain.AcademicSession;
import com.school.academics.domain.SchoolClass;
import com.school.attendance.app.HolidayService;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.BusinessRuleException;
import com.school.common.exceptions.ConflictException;
import com.school.fees.app.InvoiceService;
import com.school.fees.domain.Invoice;
import com.school.payment.app.PaymentDataResetService;
import com.school.payroll.app.PayrollService;
import com.school.people.app.EmployeeService;
import com.school.people.app.StudentService;
import com.school.schoolconfig.app.SchoolConfigService;
import com.school.schoolconfig.domain.FeeCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Seeds the demonstration data set: a whole 2026-27 college year, written through the real services.
 *
 * <p><strong>Prerequisites are checked before anything is written.</strong> The data set describes a
 * specific session, specific courses and specific people, and a half-seeded database is far harder to
 * explain than a refusal — so a missing session, the wrong fee cycle or a missing course stops it
 * with a message that says what to create. Individual <em>people</em> are the exception: those are
 * matched by name and warned about, because a demonstration college that spells one name differently
 * should still get its fees and its payroll.
 *
 * <p><strong>It refuses to run on top of real records.</strong> Money taken and salaries computed are
 * the two things that cannot be seeded twice without doubling every report, so either of them in the
 * active session is a 409 — overridable with {@code ?force=true}, which is for the case where a wipe
 * was interrupted and left something behind.
 *
 * <p>Not transactional, and could not usefully be: this writes thousands of documents across seven
 * collections and takes money through a hundred separate payment transactions, each of which has to
 * commit on its own because each draws a receipt number. A run that fails part-way leaves what it had
 * already written, which is what {@code DELETE /admin/demo-data} is for.
 */
@Service
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled", havingValue = "true")
public class DemoDataSeeder {

	private static final String AUDIT_ENTITY = "DemoData";

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	/** The session this data set is written against, by name and by dates. */
	private static final String SESSION_NAME = "2026-27";

	/** The two public holidays the term contains. */
	private static final Map<LocalDate, String> HOLIDAYS = Map.of(
			DemoCalendar.INDEPENDENCE_DAY, "Independence Day",
			DemoCalendar.GANDHI_JAYANTI, "Gandhi Jayanti");

	private final AcademicContext academicContext;
	private final SchoolClassService classes;
	private final SchoolConfigService schoolConfig;
	private final StudentService students;
	private final EmployeeService employees;
	private final HolidayService holidays;
	private final InvoiceService invoices;
	private final PaymentDataResetService paymentRecords;
	private final PayrollService payroll;
	private final DemoFeeSeeder fees;
	private final DemoStaffSeeder staff;
	private final AuditService audit;
	private final Clock clock;

	public DemoDataSeeder(AcademicContext academicContext, SchoolClassService classes,
			SchoolConfigService schoolConfig, StudentService students, EmployeeService employees,
			HolidayService holidays, InvoiceService invoices, PaymentDataResetService paymentRecords,
			PayrollService payroll, DemoFeeSeeder fees, DemoStaffSeeder staff, AuditService audit, Clock clock) {
		this.academicContext = academicContext;
		this.classes = classes;
		this.schoolConfig = schoolConfig;
		this.students = students;
		this.employees = employees;
		this.holidays = holidays;
		this.invoices = invoices;
		this.paymentRecords = paymentRecords;
		this.payroll = payroll;
		this.fees = fees;
		this.staff = staff;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Writes the whole data set and answers with what it produced.
	 *
	 * @param force skip the "nothing has been recorded yet" check. Only for re-running after a wipe
	 * @throws BusinessRuleException 422 when a prerequisite is not there, naming what to create
	 * @throws ConflictException     409 when the session already holds payments or payroll records
	 */
	public DemoSummary seed(boolean force) {
		AcademicSession session = requireDemonstrationSession();
		requireSemesterFeeCycle();
		Map<String, SchoolClass> courses = requireCourses();
		requirePeople(session);
		requireNothingRecordedYet(session, force);

		LocalDate today = LocalDate.now(clock);
		DemoSummary summary = new DemoSummary();
		seedHolidays(summary);
		fees.seed(session.getId(), courses, today, summary);
		staff.seed(today, summary);

		audit.record(AuditAction.DEMO_DATA_SEEDED, AUDIT_ENTITY, session.getId(), null, summary.asAuditCounts());
		log.info("Seeded the demonstration data set into session {}: {}", session.getName(),
				summary.asAuditCounts());
		return summary;
	}

	// --- 1. holidays ------------------------------------------------------------------------------

	/**
	 * Declares the term's two holidays, first, because the staff register refuses to be marked on one
	 * and the attendance step below would otherwise fill them in.
	 */
	private void seedHolidays(DemoSummary summary) {
		HOLIDAYS.entrySet().stream()
				// A map has no order and receipts and audit entries read better in date order.
				.sorted(Map.Entry.comparingByKey())
				.forEach(holiday -> {
					if (holidays.isHoliday(holiday.getKey())) {
						return;
					}
					holidays.create(holiday.getKey(), holiday.getValue());
					summary.addHoliday();
				});
	}

	// --- prerequisites ----------------------------------------------------------------------------

	/**
	 * The active session, which has to be the one this data set is dated for.
	 *
	 * <p>Checked on the dates rather than only on the name: every fee due date, every payment date and
	 * every payroll month in here is a literal, so writing them into a session that runs April to
	 * March would produce invoices dated outside their own year and a dashboard that could not place
	 * them.
	 */
	private AcademicSession requireDemonstrationSession() {
		AcademicSession session = academicContext.currentSession();
		if (!DemoCalendar.SESSION_START.equals(session.getStartDate())
				|| !DemoCalendar.SESSION_END.equals(session.getEndDate())) {
			throw new BusinessRuleException("The demonstration data is dated for a session running "
					+ DemoCalendar.SESSION_START + " to " + DemoCalendar.SESSION_END + ", and the active session "
					+ "\"" + session.getName() + "\" runs " + session.getStartDate() + " to "
					+ session.getEndDate() + ". Create a session called " + SESSION_NAME + " over those dates "
					+ "and activate it first.");
		}
		return session;
	}

	/**
	 * A semester fee cycle, because every structure this writes is made of {@code SEMESTER}
	 * installments. With the cycle left on months, the fee register and the dashboard would lay a
	 * semester out as though it were one, and the current-period figures would be wrong rather than
	 * merely odd.
	 */
	private void requireSemesterFeeCycle() {
		FeeCycle cycle = schoolConfig.feeCycle();
		if (cycle != FeeCycle.SEMESTER) {
			throw new BusinessRuleException("The demonstration data bills by semester, and this deployment's "
					+ "academicSettings.feeCycle is " + cycle + ". Set it to SEMESTER through "
					+ "PUT /school/config first.");
		}
	}

	/**
	 * The five courses, by the names the data set uses.
	 *
	 * @return course name to the class, so nothing downstream looks a course up twice
	 */
	private Map<String, SchoolClass> requireCourses() {
		Map<String, SchoolClass> byLowerCaseName = new LinkedHashMap<>();
		classes.list().forEach(schoolClass ->
				byLowerCaseName.putIfAbsent(schoolClass.getName().trim().toLowerCase(Locale.ROOT), schoolClass));

		Map<String, SchoolClass> courses = new LinkedHashMap<>();
		List<String> missing = new ArrayList<>();
		for (DemoCourse course : DemoCourse.ALL) {
			SchoolClass found = byLowerCaseName.get(course.courseName().toLowerCase(Locale.ROOT));
			if (found == null) {
				missing.add(course.courseName());
			}
			else if (found.getSections() == null || found.getSections().isEmpty()) {
				missing.add(course.courseName() + " (it exists but has no sections)");
			}
			else {
				courses.put(course.courseName(), found);
			}
		}
		if (!missing.isEmpty()) {
			throw new BusinessRuleException("These courses are missing, so there is nothing to bill: "
					+ String.join(", ", missing) + ". Create them through POST /classes — each needs at least "
					+ "one section — and admit students onto them first.");
		}
		return courses;
	}

	/**
	 * Somebody to bill and somebody to pay.
	 *
	 * <p>Only that there is anybody at all. Which students are on which course is not checked here:
	 * every pattern is picked from the roll as it stands, so a course with three students on it gets
	 * three patterns rather than an error.
	 */
	private void requirePeople(AcademicSession session) {
		if (students.activeCount(session.getId()) == 0L) {
			throw new BusinessRuleException("No students are enrolled in " + session.getName()
					+ ", so there is nobody to bill. Admit students — POST /students or "
					+ "POST /students/import — first.");
		}
		if (employees.activeEmployees().isEmpty()) {
			throw new BusinessRuleException("There are no active employees, so there is no payroll to run. "
					+ "Take staff on — POST /employees or POST /employees/import — first.");
		}
	}

	/**
	 * Refuses to seed on top of money already taken or salaries already computed.
	 *
	 * <p>Those two and not the rest, because they are the two that cannot be written twice harmlessly.
	 * Invoice generation is idempotent, a holiday that exists is skipped and a salary structure simply
	 * gains a version — but a second payment against the same invoice really is a second payment, with
	 * its own receipt, and a payroll run on a month that already has records skips them and leaves two
	 * data sets interleaved.
	 */
	private void requireNothingRecordedYet(AcademicSession session, boolean force) {
		if (force) {
			log.warn("Seeding the demonstration data set with force=true: the usual check for existing "
					+ "payments and payroll records was skipped");
			return;
		}
		List<String> found = new ArrayList<>();
		List<String> invoiceIds = invoices.invoicesInSession(session.getId()).stream()
				.map(Invoice::getId)
				.toList();
		if (paymentRecords.anyPaymentFor(invoiceIds)) {
			found.add("payments have been taken against this session's invoices");
		}
		if (payroll.anyRecordIn(YearMonth.from(session.getStartDate()), YearMonth.from(session.getEndDate()))) {
			found.add("payroll has been run for a month of this session");
		}
		if (!found.isEmpty()) {
			throw new ConflictException("The demonstration data was not seeded, because "
					+ String.join(" and ", found) + ". Seeding on top of that would double every collection "
					+ "report and every salary register. Wipe it with DELETE /admin/demo-data first, or — if "
					+ "a wipe was interrupted and left something behind — re-run this with ?force=true.");
		}
	}
}

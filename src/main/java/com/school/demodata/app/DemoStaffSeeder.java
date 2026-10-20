package com.school.demodata.app;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.school.attendance.app.EmployeeAttendanceService;
import com.school.attendance.app.HolidayService;
import com.school.attendance.domain.AttendanceStatus;
import com.school.attendance.domain.EmployeeAttendanceEntry;
import com.school.common.exceptions.AppException;
import com.school.payroll.app.PayrollRunResult;
import com.school.payroll.app.PayrollService;
import com.school.payroll.app.SalaryAdvanceService;
import com.school.payroll.app.SalaryStructureService;
import com.school.payroll.domain.DeductionRule;
import com.school.payroll.domain.DeductionType;
import com.school.payroll.domain.PayrollRecord;
import com.school.payroll.domain.SalaryComponent;
import com.school.payroll.domain.SalaryPaymentMode;
import com.school.people.app.EmployeeRef;
import com.school.people.app.EmployeeService;
import com.school.schoolconfig.app.SchoolConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The staff side of the demonstration data set: salary structures, one advance, a term of attendance
 * and four payroll runs.
 *
 * <p>The order is load-bearing and is the order the methods appear in. Structures come before any
 * run, because a run skips anybody with nothing agreed. <strong>Attendance comes before payroll</strong>,
 * because the runs prorate from it — the two absences, the half day and the two days' leave are what
 * make three otherwise identical months differ. And the departing employee is marked as having left
 * <em>between</em> September and October, which is what makes the October run one person shorter.
 *
 * <p>Everything is matched by designation or by name, and anything that cannot be found warns and is
 * skipped. One member of staff is deliberately left with no structure at all, so a run reports
 * somebody under {@code missingStructure} and the office has a reason to look at that screen.
 */
@Component
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled", havingValue = "true")
class DemoStaffSeeder {

	private static final Logger log = LoggerFactory.getLogger(DemoStaffSeeder.class);

	/** Rupees, as the specification states them, turned into paise exactly once. */
	private static final long RUPEE = 100L;

	/** {@code "SAL" + yyyymm + employeeUniqueId} is the reference on every salary transfer. */
	private static final DateTimeFormatter SALARY_REFERENCE_MONTH = DateTimeFormatter.ofPattern("uuuuMM",
			Locale.ENGLISH);

	// --- what each kind of employee is paid -------------------------------------------------------

	private static final String TEACHER = "TEACHER";

	/** The prefix that marks a doctorate in the names this college uses. */
	private static final String DOCTORATE = "Dr.";

	private static final long FACULTY_WITH_DOCTORATE_BASIC = 42_000 * RUPEE;
	private static final long FACULTY_BASIC = 32_000 * RUPEE;
	private static final long FACULTY_HRA = 4_000 * RUPEE;
	private static final long FACULTY_DA = 3_000 * RUPEE;

	private static final long PROFESSIONAL_TAX = 200 * RUPEE;

	/** 12% of the basic, in basis points — see {@link DeductionType#PERCENT_OF_BASIC}. */
	private static final long PROVIDENT_FUND_BASIS_POINTS = 1_200L;

	/**
	 * The non-teaching salaries, by designation.
	 *
	 * <p>The three office roles get HRA and have PF withheld; the two support roles get a smaller HRA
	 * and nothing withheld, which is how a school with a mixed payroll actually looks and what makes
	 * the register worth reading.
	 */
	private static final List<StaffSalary> STAFF_SALARIES = List.of(
			new StaffSalary("Accountant", 22_000 * RUPEE, 1_500 * RUPEE, true),
			new StaffSalary("Librarian", 18_000 * RUPEE, 1_500 * RUPEE, true),
			new StaffSalary("Office Clerk", 16_000 * RUPEE, 1_500 * RUPEE, true),
			new StaffSalary("Peon", 11_000 * RUPEE, 800 * RUPEE, false),
			new StaffSalary("Security Guard", 11_000 * RUPEE, 800 * RUPEE, false));

	/** One member of faculty whose basic is revised part-way through the year. */
	private static final String REVISED = "Dr. Rekha Singh";

	private static final long REVISED_BASIC = 45_000 * RUPEE;

	/**
	 * One member of staff left without a structure on purpose.
	 *
	 * <p>Every payroll run then reports her under {@code missingStructure}, which is the state an
	 * office most needs to be able to see: somebody who will not be paid until a figure is agreed.
	 */
	private static final String WITHOUT_STRUCTURE = "Smt. Lalita Devi";

	private static final String ADVANCE_TO = "Shri Pramod Kumar";

	private static final long ADVANCE_AMOUNT = 6_000 * RUPEE;

	private static final long ADVANCE_MONTHLY_RECOVERY = 2_000 * RUPEE;

	/** The one employee who leaves, once September's salary has been paid. */
	private static final String LEAVER = "Shri Jagdish Yadav";

	/**
	 * Who was not simply PRESENT, and when.
	 *
	 * <p>Three people and five days across a whole term, which is about the real rate — and enough to
	 * make the prorated runs differ: the two absences and the half day cost money, while the two days'
	 * approved leave deliberately do not.
	 */
	private static final List<AttendanceException> ATTENDANCE_EXCEPTIONS = List.of(
			new AttendanceException("Shri Anil Kumar Yadav", LocalDate.of(2026, 9, 10), AttendanceStatus.ABSENT),
			new AttendanceException("Shri Anil Kumar Yadav", LocalDate.of(2026, 9, 11), AttendanceStatus.ABSENT),
			new AttendanceException("Smt. Kavita Mishra", LocalDate.of(2026, 8, 14), AttendanceStatus.HALF_DAY),
			new AttendanceException("Shri Bechan Ram", LocalDate.of(2026, 10, 5), AttendanceStatus.LEAVE),
			new AttendanceException("Shri Bechan Ram", LocalDate.of(2026, 10, 6), AttendanceStatus.LEAVE));

	private final SalaryStructureService salaryStructures;
	private final SalaryAdvanceService advances;
	private final PayrollService payroll;
	private final EmployeeAttendanceService attendance;
	private final HolidayService holidays;
	private final EmployeeService employees;
	private final SchoolConfigService schoolConfig;

	DemoStaffSeeder(SalaryStructureService salaryStructures, SalaryAdvanceService advances, PayrollService payroll,
			EmployeeAttendanceService attendance, HolidayService holidays, EmployeeService employees,
			SchoolConfigService schoolConfig) {
		this.salaryStructures = salaryStructures;
		this.advances = advances;
		this.payroll = payroll;
		this.attendance = attendance;
		this.holidays = holidays;
		this.employees = employees;
		this.schoolConfig = schoolConfig;
	}

	/** @param today from the injected clock; attendance stops at yesterday and payroll at this month */
	void seed(LocalDate today, DemoSummary summary) {
		List<EmployeeRef> staff = employees.activeEmployees();
		seedSalaryStructures(staff, summary);
		seedAdvance(staff, summary);
		seedAttendance(staff, today, summary);
		seedPayroll(today, summary);
	}

	// --- 7. salary structures ---------------------------------------------------------------------

	private void seedSalaryStructures(List<EmployeeRef> staff, DemoSummary summary) {
		List<DeductionRule> officeDeductions = List.of(
				new DeductionRule("PF", DeductionType.PERCENT_OF_BASIC, PROVIDENT_FUND_BASIS_POINTS),
				new DeductionRule("Professional tax", DeductionType.FIXED, PROFESSIONAL_TAX));

		for (EmployeeRef employee : staff) {
			if (WITHOUT_STRUCTURE.equalsIgnoreCase(trim(employee.name()))) {
				// The point of the exercise: a run has to have somebody to report as unpayable.
				continue;
			}
			if (TEACHER.equals(employee.employeeType())) {
				boolean doctorate = trim(employee.name()).startsWith(DOCTORATE);
				save(employee, doctorate ? FACULTY_WITH_DOCTORATE_BASIC : FACULTY_BASIC,
						List.of(new SalaryComponent("HRA", FACULTY_HRA), new SalaryComponent("DA", FACULTY_DA)),
						officeDeductions, DemoCalendar.SALARIES_FROM, summary);
				continue;
			}
			Optional<StaffSalary> rule = staffSalaryFor(employee.designation());
			if (rule.isEmpty()) {
				summary.warn(employee.name() + " has the designation "
						+ (employee.designation() == null ? "(none)" : "\"" + employee.designation() + "\"")
						+ ", which this data set has no salary for, so none was agreed.");
				continue;
			}
			save(employee, rule.get().basic(), List.of(new SalaryComponent("HRA", rule.get().hra())),
					rule.get().withDeductions()
							? List.of(new DeductionRule("PF", DeductionType.PERCENT_OF_BASIC,
									PROVIDENT_FUND_BASIS_POINTS))
							: List.of(),
					DemoCalendar.SALARIES_FROM, summary);
		}

		seedRevision(staff, officeDeductions, summary);
	}

	/**
	 * The one mid-year revision, written as a further version rather than an edit.
	 *
	 * <p>Nothing overwrites anything in payroll, so after this there are two versions on file and the
	 * July, August and September records keep the figure they were computed from while October's is
	 * higher. That is the behaviour this revision exists to demonstrate.
	 */
	private void seedRevision(List<EmployeeRef> staff, List<DeductionRule> deductions, DemoSummary summary) {
		Optional<EmployeeRef> revised = byName(staff, REVISED);
		if (revised.isEmpty()) {
			summary.warn("No active employee called " + REVISED + ", so no salary revision was recorded.");
			return;
		}
		save(revised.get(), REVISED_BASIC,
				List.of(new SalaryComponent("HRA", FACULTY_HRA), new SalaryComponent("DA", FACULTY_DA)),
				deductions, DemoCalendar.REVISION_FROM, summary);
	}

	private void save(EmployeeRef employee, long basic, List<SalaryComponent> allowances,
			List<DeductionRule> deductions, LocalDate effectiveFrom, DemoSummary summary) {
		salaryStructures.saveVersion(employee.uniqueId(), basic, allowances, deductions, effectiveFrom);
		summary.addSalaryStructure();
	}

	/** The rule for a designation, matched on the trimmed text and ignoring case. */
	private static Optional<StaffSalary> staffSalaryFor(String designation) {
		String trimmed = trim(designation);
		if (trimmed.isEmpty()) {
			return Optional.empty();
		}
		return STAFF_SALARIES.stream()
				.filter(salary -> salary.designation().equalsIgnoreCase(trimmed))
				.findFirst();
	}

	// --- 8. the advance ---------------------------------------------------------------------------

	private void seedAdvance(List<EmployeeRef> staff, DemoSummary summary) {
		Optional<EmployeeRef> employee = byName(staff, ADVANCE_TO);
		if (employee.isEmpty()) {
			summary.warn("No active employee called " + ADVANCE_TO + ", so no advance was given.");
			return;
		}
		advances.give(employee.get().uniqueId(), ADVANCE_AMOUNT, DemoCalendar.ADVANCE_GIVEN_ON,
				ADVANCE_MONTHLY_RECOVERY, "Demonstration data: advance against salary");
		summary.addSalaryAdvance();
		log.info("Gave {} an advance of {} paise, recovered {} a month", employee.get().uniqueId(),
				ADVANCE_AMOUNT, ADVANCE_MONTHLY_RECOVERY);
	}

	// --- 9. staff attendance ----------------------------------------------------------------------

	/**
	 * Marks every working day from the start of the session up to yesterday.
	 *
	 * <p><strong>Today is deliberately left unmarked</strong>, so the register screen opens on a day
	 * that still needs doing rather than on one already filled in — which is the state an office
	 * actually finds it in.
	 *
	 * <p>Which days are working days comes from {@code academicSettings.workingDays} rather than from
	 * a list here, so a college that works a six-day week and one that works five both get a register
	 * that agrees with their own calendar (CLAUDE.md rule 6). Holidays are skipped because the register
	 * refuses them, which is also why the holidays have to have been declared before this runs.
	 */
	private void seedAttendance(List<EmployeeRef> staff, LocalDate today, DemoSummary summary) {
		Set<DayOfWeek> workingDays = schoolConfig.workingDays();
		Map<String, Map<LocalDate, AttendanceStatus>> exceptions = exceptionsBy(staff, summary);
		LocalDate until = today.minusDays(1);

		for (LocalDate date = DemoCalendar.SESSION_START; !date.isAfter(until); date = date.plusDays(1)) {
			if (!workingDays.contains(date.getDayOfWeek()) || holidays.isHoliday(date)) {
				continue;
			}
			LocalDate day = date;
			List<EmployeeAttendanceEntry> marks = staff.stream()
					.map(employee -> new EmployeeAttendanceEntry(employee.uniqueId(),
							exceptions.getOrDefault(employee.uniqueId(), Map.of())
									.getOrDefault(day, AttendanceStatus.PRESENT)))
					.toList();
			try {
				summary.addAttendanceDay(attendance.markDay(day, marks));
			}
			catch (AppException ex) {
				summary.warn("The staff register for " + day + " was refused: " + ex.getMessage());
			}
		}
		log.info("Marked {} staff registers from {} to {}", summary.attendanceDaysMarked(),
				DemoCalendar.SESSION_START, until);
	}

	/** The exceptions keyed the way the loop reads them: employee, then date. */
	private Map<String, Map<LocalDate, AttendanceStatus>> exceptionsBy(List<EmployeeRef> staff,
			DemoSummary summary) {
		Map<String, Map<LocalDate, AttendanceStatus>> byEmployee = new LinkedHashMap<>();
		List<String> missing = new ArrayList<>();
		for (AttendanceException exception : ATTENDANCE_EXCEPTIONS) {
			Optional<EmployeeRef> employee = byName(staff, exception.employeeName());
			if (employee.isEmpty()) {
				if (!missing.contains(exception.employeeName())) {
					missing.add(exception.employeeName());
				}
				continue;
			}
			byEmployee.computeIfAbsent(employee.get().uniqueId(), key -> new LinkedHashMap<>())
					.put(exception.date(), exception.status());
		}
		missing.forEach(name -> summary.warn("No active employee called " + name
				+ ", so their absences were not marked and they are PRESENT throughout."));
		return byEmployee;
	}

	// --- 10. payroll ------------------------------------------------------------------------------

	/**
	 * Runs four months, pays three of them, and lets one employee go in between.
	 *
	 * <p>Prorated, so the absences marked above are docked and the approved leave is not. October is
	 * run and left PENDING on purpose: the admin dashboard's "salaries owed" figure has to be
	 * something other than zero, and an office that has computed a month but not paid it is the normal
	 * state halfway through one.
	 */
	private void seedPayroll(LocalDate today, DemoSummary summary) {
		YearMonth thisMonth = YearMonth.from(today);
		for (YearMonth month : DemoCalendar.PAID_PAYROLL_MONTHS) {
			if (!run(month, thisMonth, summary)) {
				continue;
			}
			// The last day of the month, or today when the month has not finished yet: a salary is
			// recorded as it goes out, so payroll refuses a date in the future.
			LocalDate paidOn = earlier(month.atEndOfMonth(), today);
			int paid = 0;
			for (PayrollRecord record : payroll.pendingRecords(month)) {
				try {
					payroll.payRecord(record.getId(), SalaryPaymentMode.BANK_TRANSFER,
							salaryReference(month, record.getEmployeeUniqueId()), paidOn);
					paid++;
				}
				catch (AppException ex) {
					summary.warn("The salary of " + record.getEmployeeName() + " for " + month
							+ " was not paid: " + ex.getMessage());
				}
			}
			summary.addPayrollPaid(month.toString(), paid);
			log.info("Paid {} salaries for {} on {}", paid, month, paidOn);
		}

		letTheLeaverGo(summary);
		run(DemoCalendar.PENDING_PAYROLL_MONTH, thisMonth, summary);
	}

	/** @return false when the month has not happened yet, in which case nothing was run */
	private boolean run(YearMonth month, YearMonth thisMonth, DemoSummary summary) {
		if (month.isAfter(thisMonth)) {
			summary.warn("Payroll for " + month + " was not run: that month has not happened yet. This data "
					+ "set describes the 2026-27 session, so run it inside that year.");
			return false;
		}
		PayrollRunResult result = payroll.runFor(month, true);
		summary.addPayrollRun(result.month(), result.created());
		result.missingStructure().forEach(who -> summary.warn("Payroll for " + month + " could not pay " + who
				+ ": no salary structure is effective for that month."));
		return true;
	}

	/**
	 * Lets one employee go, after September's salary has been paid and before October is run.
	 *
	 * <p>The order is the point. Paid first, so his three months exist and his slips still print;
	 * withdrawn before the October run, so that run is one record shorter. His staff attendance was
	 * marked while he was still active, which is what the register of a person who left mid-year looks
	 * like.
	 */
	private void letTheLeaverGo(DemoSummary summary) {
		Optional<EmployeeRef> leaver = byName(employees.activeEmployees(), LEAVER);
		if (leaver.isEmpty()) {
			summary.warn("No active employee called " + LEAVER + ", so nobody was marked as having left on "
					+ DemoCalendar.LEFT_ON + ".");
			return;
		}
		employees.markLeft(leaver.get().uniqueId());
		summary.addEmployeeMarkedLeft();
		log.info("{} was marked as having left as of {}", leaver.get().uniqueId(), DemoCalendar.LEFT_ON);
	}

	/** {@code SAL202609DEMO-EMP-26-00004}: the month and the person, which is what a bank line needs. */
	private static String salaryReference(YearMonth month, String employeeUniqueId) {
		return "SAL" + month.format(SALARY_REFERENCE_MONTH) + employeeUniqueId;
	}

	// --- internals --------------------------------------------------------------------------------

	/** The one active employee with that name, if there is one. Matched on the trimmed name. */
	private static Optional<EmployeeRef> byName(List<EmployeeRef> staff, String name) {
		return staff.stream().filter(employee -> name.equalsIgnoreCase(trim(employee.name()))).findFirst();
	}

	private static LocalDate earlier(LocalDate first, LocalDate second) {
		return first.isAfter(second) ? second : first;
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

	/**
	 * What one non-teaching designation is paid.
	 *
	 * @param withDeductions whether PF is withheld. The support roles are below the threshold a school
	 *                       of this size withholds at, which is why they have none
	 */
	private record StaffSalary(String designation, long basic, long hra, boolean withDeductions) {
	}

	/** One day on which one named employee was not simply PRESENT. */
	private record AttendanceException(String employeeName, LocalDate date, AttendanceStatus status) {
	}
}

package com.school.fees.app;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.school.academics.app.AcademicContext;
import com.school.academics.app.SchoolClassService;
import com.school.academics.domain.SchoolClass;
import com.school.fees.domain.FeePeriod;
import com.school.fees.domain.InstallmentKind;
import com.school.fees.domain.Invoice;
import com.school.fees.domain.InvoiceStatus;
import com.school.fees.infra.InvoiceRepository;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import com.school.schoolconfig.app.SchoolConfigService;
import com.school.schoolconfig.domain.FeeCycle;
import org.springframework.stereotype.Service;

/**
 * The fee half of the admin dashboard (B16): where the session stands, where the period being
 * collected stands class by class, and which one-off charges are still outstanding.
 *
 * <p><strong>Three reads for all of it</strong>, whatever the size of the institution: the session's
 * invoices, the session's roll and the class list. The alternative — a query per class-section —
 * is forty round trips on a screen that loads on every admin login, and it is the reason this is one
 * method returning one object rather than three the controller calls in turn.
 *
 * <p><strong>"This period" is read off the invoices, not off the calendar.</strong> Each student is
 * counted against the recurring installment — MONTHLY or SEMESTER — whose period contains today, which
 * is the only rule that works when one college runs some courses monthly and others by semester.
 * {@code academicSettings.feeCycle} decides the <em>heading</em>: it names the window when the
 * invoices agree on nothing, and it is the fallback before anything has been billed at all. It gates
 * nothing, so a figure never disappears because the configuration says MONTHLY and a course charges
 * semesters.
 *
 * <p>This is a read model and writes nothing, so there is no audit entry: nothing happened.
 */
@Service
public class FeeDashboardService {

	/** Everything but CANCELLED. A withdrawn invoice is in no total here; see {@link FeeDashboard}. */
	private static final Set<InvoiceStatus> LIVE =
			Set.of(InvoiceStatus.UNPAID, InvoiceStatus.PARTIAL, InvoiceStatus.PAID);

	private final InvoiceRepository invoices;
	private final StudentService students;
	private final SchoolClassService classes;
	private final AcademicContext academicContext;
	private final SchoolConfigService config;
	private final Clock clock;

	public FeeDashboardService(InvoiceRepository invoices, StudentService students, SchoolClassService classes,
			AcademicContext academicContext, SchoolConfigService config, Clock clock) {
		this.invoices = invoices;
		this.students = students;
		this.classes = classes;
		this.academicContext = academicContext;
		this.config = config;
		this.clock = clock;
	}

	/**
	 * Every fee figure the dashboard shows.
	 *
	 * <p>Empty rather than an error when no session is active: a deployment on its first morning has
	 * no session and no invoices, and a dashboard that 422s is a dashboard the administrator cannot
	 * use to find the screen that fixes it.
	 */
	public FeeDashboard snapshot() {
		LocalDate today = LocalDate.now(clock);
		FeeCycle cycle = config.feeCycle();
		if (!academicContext.hasCurrentSession()) {
			return FeeDashboard.empty(today, fallbackPeriod(cycle, today));
		}
		String sessionId = academicContext.currentSessionId();
		List<Invoice> live = invoices.findBySessionIdAndStatusIn(sessionId, LIVE);
		Map<String, SchoolClass> byId = new LinkedHashMap<>();
		classes.list().stream()
				.sorted(Comparator.comparingInt(SchoolClass::getOrder).thenComparing(SchoolClass::getName))
				.forEach(schoolClass -> byId.put(schoolClass.getId(), schoolClass));

		// The invoices of the period being collected: whichever recurring installment covers today, per
		// student. Everything below counts off this one list.
		List<Invoice> covering = live.stream().filter(invoice -> invoice.coversRecurring(today)).toList();
		FeeDashboard.CurrentPeriod currentPeriod = currentPeriod(covering, cycle, today);
		List<FeeDashboard.ClassPeriod> byClass = periodByClass(covering, sessionId, byId);

		return new FeeDashboard(sessionTotals(live), currentPeriod, byClass,
				// The same figures under the old names, for one release. See FeeDashboard.
				new FeeDashboard.MonthTotals(YearMonth.from(today), currentPeriod.totalDue(),
						currentPeriod.collected(), currentPeriod.pending()),
				byClass,
				pendingOneTime(live, byId));
	}

	// --- the session -------------------------------------------------------------------------------

	private static FeeDashboard.SessionTotals sessionTotals(List<Invoice> live) {
		long totalDue = 0L;
		long collected = 0L;
		long pending = 0L;
		for (Invoice invoice : live) {
			totalDue += invoice.getNetAmount();
			collected += invoice.getPaidAmount();
			pending += invoice.outstanding();
		}
		return new FeeDashboard.SessionTotals(totalDue, collected, pending);
	}

	// --- the period being collected ---------------------------------------------------------------

	/**
	 * The heading over the period's figures, and the figures themselves.
	 *
	 * <p>The window spans the covering invoices: the earliest period start and the latest period end
	 * among them. For a monthly deployment that is exactly the calendar month; for a college whose
	 * courses run their semesters on slightly different dates it is the stretch that contains all of
	 * them, which is the honest answer to "what are we collecting".
	 *
	 * <p>The label is the one every covering invoice agrees on — "Semester 3 (Jul-Dec 2026)" when the
	 * whole institution is on the same semester — and otherwise a plain description of the window, so a
	 * college with Semester 1 and Semester 3 running together is headed "Jul-Dec 2026" rather than being
	 * told it is collecting somebody's semester 1. With nothing billed yet there is nothing to read, so
	 * the configured cycle picks the window: the calendar month, or the whole of the session's half.
	 */
	private FeeDashboard.CurrentPeriod currentPeriod(List<Invoice> covering, FeeCycle cycle, LocalDate today) {
		long totalDue = 0L;
		long collected = 0L;
		long pending = 0L;
		LocalDate start = null;
		LocalDate end = null;
		String commonLabel = null;
		boolean labelsAgree = true;
		for (Invoice invoice : covering) {
			totalDue += invoice.getNetAmount();
			collected += invoice.getPaidAmount();
			pending += invoice.outstanding();
			FeePeriod period = invoice.period();
			if (start == null || period.start().isBefore(start)) {
				start = period.start();
			}
			if (end == null || period.end().isAfter(end)) {
				end = period.end();
			}
			if (commonLabel == null) {
				commonLabel = invoice.displayLabel();
			}
			else if (!commonLabel.equals(invoice.displayLabel())) {
				labelsAgree = false;
			}
		}

		FeePeriod window = start == null ? fallbackPeriod(cycle, today) : new FeePeriod(start, end);
		String label = labelsAgree && commonLabel != null ? commonLabel : window.describe();
		return new FeeDashboard.CurrentPeriod(label, window.start(), window.end(), totalDue, collected, pending);
	}

	/**
	 * The window to head the dashboard with when nothing has been billed for it yet — the one case
	 * where {@code feeCycle} is the only thing there is to go on.
	 *
	 * <p>A semester deployment gets the half of the calendar year today falls in, July to December or
	 * January to June, which is the split {@code /fees/structures/semester/suggest} proposes. It is a
	 * guess about an empty screen, and it stops being one the moment a single invoice exists.
	 */
	private static FeePeriod fallbackPeriod(FeeCycle cycle, LocalDate today) {
		if (cycle != FeeCycle.SEMESTER) {
			return FeePeriod.ofMonthContaining(today);
		}
		return today.getMonthValue() >= 7
				? new FeePeriod(LocalDate.of(today.getYear(), 7, 1), LocalDate.of(today.getYear(), 12, 31))
				: new FeePeriod(LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 6, 30));
	}

	/**
	 * One row per class-section on the roll, in class order then section.
	 *
	 * <p>Rows come from the <em>roll</em>, not from the invoices, so a section nobody has billed yet
	 * still appears — with its student count and zeros — which is how an office spots that it forgot to
	 * generate October. An invoice belonging to somebody no longer on the roll is left out, the same
	 * way the fee register leaves it out: there is no row for it to land on.
	 *
	 * <p>The section comes from the student's current enrollment rather than from the invoice, which
	 * carries only a class. A student moved between sections mid-period therefore counts in the section
	 * they are in now, which is the one whose register the office is looking at.
	 */
	private List<FeeDashboard.ClassPeriod> periodByClass(List<Invoice> covering, String sessionId,
			Map<String, SchoolClass> byId) {
		Map<String, StudentRef> roll = new LinkedHashMap<>();
		students.activeInSession(sessionId).forEach(student -> roll.put(student.uniqueId(), student));

		// Keyed by classId + '/' + section, inserted in class-then-section order so the output needs no
		// final sort: the class list arrives ordered and each class's sections are already in display
		// order on the document.
		Map<String, Bucket> buckets = new LinkedHashMap<>();
		byId.values().forEach(schoolClass -> {
			if (schoolClass.getSections() == null) {
				return;
			}
			schoolClass.getSections().forEach(section ->
					buckets.put(key(schoolClass.getId(), section), new Bucket(schoolClass, section)));
		});

		// A student's period, settled or not, before anything is counted: one student with two invoices
		// covering today and owing on one of them is one unpaid student, not one paid and one unpaid.
		Map<String, long[]> perStudent = new LinkedHashMap<>();
		// What each class calls the period, taken off its own invoices rather than invented here.
		Map<String, String> labels = new LinkedHashMap<>();
		for (Invoice invoice : covering) {
			if (!roll.containsKey(invoice.getStudentUniqueId())) {
				continue;
			}
			long[] totals = perStudent.computeIfAbsent(invoice.getStudentUniqueId(), unused -> new long[2]);
			totals[0] += invoice.getPaidAmount();
			totals[1] += invoice.outstanding();
			labels.putIfAbsent(invoice.getClassId(), invoice.displayLabel());
		}

		for (StudentRef student : roll.values()) {
			Bucket bucket = buckets.get(key(student.classId(), student.section()));
			if (bucket == null) {
				// Enrolled in a class or section that has since been removed from the class list. Counted
				// nowhere rather than in a row invented for it, which would be a class the office cannot
				// click through to.
				continue;
			}
			bucket.students++;
			long[] totals = perStudent.get(student.uniqueId());
			if (totals == null) {
				// Not billed for this period. In neither count, deliberately — see FeeDashboard.ClassPeriod.
				continue;
			}
			bucket.collected += totals[0];
			bucket.pending += totals[1];
			if (totals[1] > 0L) {
				bucket.unpaidCount++;
			}
			else {
				bucket.paidCount++;
			}
		}

		List<FeeDashboard.ClassPeriod> rows = new ArrayList<>(buckets.size());
		buckets.values().forEach(bucket -> rows.add(new FeeDashboard.ClassPeriod(bucket.schoolClass.getId(),
				bucket.schoolClass.getName(), bucket.section, labels.get(bucket.schoolClass.getId()),
				bucket.students, bucket.paidCount, bucket.unpaidCount, bucket.collected, bucket.pending)));
		return rows;
	}

	// --- one-off charges still owed ---------------------------------------------------------------

	/**
	 * Outstanding ONE_TIME charges, one row per charge per class, heaviest first.
	 *
	 * <p>Ordered by what is owed rather than by class, unlike the period grid: this list is read to
	 * decide what to chase, and the biggest hole is the answer. CUSTOM charges — one student's lost
	 * library book — are not here; they are not a charge the class shares, and the defaulters list
	 * already carries them.
	 */
	private static List<FeeDashboard.PendingOneTime> pendingOneTime(List<Invoice> live,
			Map<String, SchoolClass> byId) {
		Map<String, OneTimeTally> tallies = new LinkedHashMap<>();
		for (Invoice invoice : live) {
			if (invoice.resolvedKind() != InstallmentKind.ONE_TIME || invoice.outstanding() <= 0L) {
				continue;
			}
			// Grouped on the installment name, which is stable, and labelled with displayLabel(), which
			// is what the office calls it. The class is the one the invoice was raised against.
			String groupKey = key(invoice.getClassId(), invoice.getInstallmentName());
			tallies.computeIfAbsent(groupKey, unused -> new OneTimeTally(invoice.displayLabel(),
					invoice.getClassId())).add(invoice.outstanding());
		}

		List<FeeDashboard.PendingOneTime> rows = new ArrayList<>(tallies.size());
		tallies.values().forEach(tally -> {
			SchoolClass schoolClass = byId.get(tally.classId);
			rows.add(new FeeDashboard.PendingOneTime(tally.label, tally.classId,
					schoolClass == null ? null : schoolClass.getName(), tally.unpaidCount, tally.pending));
		});
		rows.sort(Comparator.comparingLong(FeeDashboard.PendingOneTime::pending).reversed()
				.thenComparing(FeeDashboard.PendingOneTime::label));
		return rows;
	}

	// --- internals --------------------------------------------------------------------------------

	private static String key(String first, String second) {
		return first + "/" + second;
	}

	/** One class-section's running totals while the roll is walked. */
	private static final class Bucket {

		private final SchoolClass schoolClass;
		private final String section;
		private int students;
		private int paidCount;
		private int unpaidCount;
		private long collected;
		private long pending;

		private Bucket(SchoolClass schoolClass, String section) {
			this.schoolClass = schoolClass;
			this.section = section;
		}
	}

	/** One one-off charge's running totals. */
	private static final class OneTimeTally {

		private final String label;
		private final String classId;
		private int unpaidCount;
		private long pending;

		private OneTimeTally(String label, String classId) {
			this.label = label;
			this.classId = classId;
		}

		void add(long outstanding) {
			unpaidCount++;
			pending += outstanding;
		}
	}
}

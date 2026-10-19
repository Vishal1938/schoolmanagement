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
import com.school.fees.domain.InstallmentKind;
import com.school.fees.domain.Invoice;
import com.school.fees.domain.InvoiceStatus;
import com.school.fees.infra.InvoiceRepository;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import org.springframework.stereotype.Service;

/**
 * The fee half of the admin dashboard (B16): where the session stands, where this month stands
 * class by class, and which one-off charges are still outstanding.
 *
 * <p><strong>Three reads for all of it</strong>, whatever the size of the institution: the session's
 * invoices, the session's roll and the class list. The alternative — a query per class-section —
 * is forty round trips on a screen that loads on every admin login, and it is the reason this is one
 * method returning one object rather than three the controller calls in turn.
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
	private final Clock clock;

	public FeeDashboardService(InvoiceRepository invoices, StudentService students, SchoolClassService classes,
			AcademicContext academicContext, Clock clock) {
		this.invoices = invoices;
		this.students = students;
		this.classes = classes;
		this.academicContext = academicContext;
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
		YearMonth month = YearMonth.from(LocalDate.now(clock));
		if (!academicContext.hasCurrentSession()) {
			return FeeDashboard.empty(month);
		}
		String sessionId = academicContext.currentSessionId();
		List<Invoice> live = invoices.findBySessionIdAndStatusIn(sessionId, LIVE);
		Map<String, SchoolClass> byId = new LinkedHashMap<>();
		classes.list().stream()
				.sorted(Comparator.comparingInt(SchoolClass::getOrder).thenComparing(SchoolClass::getName))
				.forEach(schoolClass -> byId.put(schoolClass.getId(), schoolClass));

		return new FeeDashboard(sessionTotals(live), monthTotals(live, month),
				monthByClass(live, month, sessionId, byId), pendingOneTime(live, byId));
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

	// --- this month, class by class ---------------------------------------------------------------

	private static FeeDashboard.MonthTotals monthTotals(List<Invoice> live, YearMonth month) {
		long totalDue = 0L;
		long collected = 0L;
		long pending = 0L;
		for (Invoice invoice : live) {
			if (!isMonthOf(invoice, month)) {
				continue;
			}
			totalDue += invoice.getNetAmount();
			collected += invoice.getPaidAmount();
			pending += invoice.outstanding();
		}
		return new FeeDashboard.MonthTotals(month, totalDue, collected, pending);
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
	 * carries only a class. A student moved between sections mid-month therefore counts in the section
	 * they are in now, which is the one whose register the office is looking at.
	 */
	private List<FeeDashboard.ClassMonth> monthByClass(List<Invoice> live, YearMonth month, String sessionId,
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

		// A student's month, settled or not, before anything is counted: one student with two monthly
		// invoices owing on one of them is one unpaid student, not one paid and one unpaid.
		Map<String, long[]> perStudent = new LinkedHashMap<>();
		for (Invoice invoice : live) {
			if (!isMonthOf(invoice, month) || !roll.containsKey(invoice.getStudentUniqueId())) {
				continue;
			}
			long[] totals = perStudent.computeIfAbsent(invoice.getStudentUniqueId(), unused -> new long[2]);
			totals[0] += invoice.getPaidAmount();
			totals[1] += invoice.outstanding();
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
				// Not billed this month. In neither count, deliberately — see FeeDashboard.ClassMonth.
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

		List<FeeDashboard.ClassMonth> rows = new ArrayList<>(buckets.size());
		buckets.values().forEach(bucket -> rows.add(new FeeDashboard.ClassMonth(bucket.schoolClass.getId(),
				bucket.schoolClass.getName(), bucket.section, bucket.students, bucket.paidCount,
				bucket.unpaidCount, bucket.collected, bucket.pending)));
		return rows;
	}

	// --- one-off charges still owed ---------------------------------------------------------------

	/**
	 * Outstanding ONE_TIME charges, one row per charge per class, heaviest first.
	 *
	 * <p>Ordered by what is owed rather than by class, unlike the month grid: this list is read to
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

	/** A MONTHLY invoice falling due in that month. One with no due date falls due in no month. */
	private static boolean isMonthOf(Invoice invoice, YearMonth month) {
		return invoice.resolvedKind() == InstallmentKind.MONTHLY
				&& invoice.getDueDate() != null
				&& YearMonth.from(invoice.getDueDate()).equals(month);
	}

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

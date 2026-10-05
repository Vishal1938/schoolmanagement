package com.school.fees.app;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.school.academics.app.AcademicContext;
import com.school.academics.app.SchoolClassService;
import com.school.academics.domain.SchoolClass;
import com.school.fees.api.DefaulterResponse;
import com.school.fees.domain.Invoice;
import com.school.fees.domain.InvoiceStatus;
import com.school.fees.domain.LateFineRule;
import com.school.fees.infra.InvoiceRepository;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import org.springframework.stereotype.Service;

/**
 * The defaulters list: who still owes something, how much, and since when.
 *
 * <p>The collection report is deliberately not here. It is a report of <em>payments</em> — totals by
 * mode, by head and by day — and payments are the payment module's, which already depends on this
 * one. Putting it there keeps the dependency running one way; it is still served under
 * {@code /fees/reports/} so the two read as one group to a client.
 */
@Service
public class FeeReportService {

	/** Anything not settled and not withdrawn. */
	private static final Set<InvoiceStatus> OWED = Set.of(InvoiceStatus.UNPAID, InvoiceStatus.PARTIAL);

	private final InvoiceRepository invoices;
	private final FeeStructureService structures;
	private final StudentService students;
	private final SchoolClassService classes;
	private final AcademicContext academicContext;
	private final Clock clock;

	public FeeReportService(InvoiceRepository invoices, FeeStructureService structures, StudentService students,
			SchoolClassService classes, AcademicContext academicContext, Clock clock) {
		this.invoices = invoices;
		this.structures = structures;
		this.students = students;
		this.classes = classes;
		this.academicContext = academicContext;
		this.clock = clock;
	}

	/**
	 * Students with a balance above zero, worst first.
	 *
	 * <p>Ordered by the oldest unpaid due date rather than by the amount: the office chases the family
	 * that has been behind longest, and a large bill that fell due yesterday is not yet a problem.
	 *
	 * @param sessionId defaults to the active session
	 * @param classId   optional; omit for the whole school
	 */
	public List<DefaulterResponse> defaulters(String sessionId, String classId) {
		String session = hasText(sessionId) ? sessionId.trim() : academicContext.currentSessionId();
		List<Invoice> owed = hasText(classId)
				? invoices.findBySessionIdAndClassIdAndStatusIn(session, classId.trim(), OWED)
				: invoices.findBySessionIdAndStatusIn(session, OWED);
		if (owed.isEmpty()) {
			return List.of();
		}

		Map<String, LateFineRule> rules = structures.lateFineRulesOf(owed.stream()
				.map(Invoice::getStructureId)
				.collect(Collectors.toSet()));
		LocalDate today = LocalDate.now(clock);
		Map<String, String> classNames = classes.list().stream()
				.collect(Collectors.toMap(SchoolClass::getId, SchoolClass::getName));

		// One accumulator per student, in the order their first invoice came back.
		Map<String, Tally> byStudent = new LinkedHashMap<>();
		for (Invoice invoice : owed) {
			long lateFine = LateFine.dueOn(invoice, rules.get(invoice.getStructureId()), today);
			byStudent.computeIfAbsent(invoice.getStudentUniqueId(), uniqueId -> new Tally())
					.add(invoice, lateFine);
		}

		List<DefaulterResponse> rows = new ArrayList<>(byStudent.size());
		byStudent.forEach((uniqueId, tally) -> {
			if (tally.balance() <= 0L) {
				// Can happen for an invoice left PARTIAL with nothing actually outstanding; it is not a
				// defaulter, so it does not belong on the list.
				return;
			}
			// A student whose record has since gone is still reported, by id, rather than dropped —
			// money owed does not stop being owed because the roll was edited.
			StudentRef student = students.findRef(uniqueId).orElse(null);
			rows.add(new DefaulterResponse(uniqueId,
					student == null ? null : student.name(),
					tally.classId,
					classNames.get(tally.classId),
					student == null ? null : student.section(),
					tally.balance(), tally.lateFine, tally.oldestDueDate, tally.invoiceCount));
		});
		rows.sort(Comparator.comparing(DefaulterResponse::oldestDueDate,
						Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(DefaulterResponse::balance, Comparator.reverseOrder()));
		return rows;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	/** One student's running totals while the invoice list is walked. */
	private static final class Tally {

		private long outstanding;
		private long lateFine;
		private LocalDate oldestDueDate;
		private String classId;
		private int invoiceCount;

		void add(Invoice invoice, long fine) {
			outstanding += invoice.outstanding();
			lateFine += fine;
			invoiceCount++;
			classId = invoice.getClassId();
			if (invoice.getDueDate() != null
					&& (oldestDueDate == null || invoice.getDueDate().isBefore(oldestDueDate))) {
				oldestDueDate = invoice.getDueDate();
			}
		}

		long balance() {
			return outstanding + lateFine;
		}
	}
}

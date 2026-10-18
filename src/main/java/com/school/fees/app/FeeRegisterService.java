package com.school.fees.app;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.school.academics.app.AcademicContext;
import com.school.academics.app.SchoolClassService;
import com.school.academics.domain.SchoolClass;
import com.school.fees.api.FeeRegisterResponse;
import com.school.fees.domain.FeeStructure;
import com.school.fees.domain.InstallmentKind;
import com.school.fees.domain.Installment;
import com.school.fees.domain.Invoice;
import com.school.fees.domain.InvoiceStatus;
import com.school.fees.infra.InvoiceRepository;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import org.springframework.stereotype.Service;

/**
 * The fee register: one class laid out as a grid, students down and installments across.
 *
 * <p>Built in four reads whatever the size of the class — the structure, the roster, the invoices of
 * that roster, and the class name — rather than a query per cell. A college course with two hundred
 * students and fourteen columns is twenty-eight hundred cells, and a register that takes a second to
 * load is a register nobody opens.
 *
 * <p>Late fines are not in any figure here, on purpose; see {@link FeeRegisterResponse}.
 */
@Service
public class FeeRegisterService {

	/** Months first, then one-off fees, each block oldest due date first. */
	private static final Comparator<Installment> COLUMN_ORDER = Comparator
			.comparingInt((Installment installment) -> installment.resolvedKind() == InstallmentKind.ONE_TIME ? 1 : 0)
			.thenComparing(Installment::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(Installment::name);

	private final InvoiceRepository invoices;
	private final FeeStructureService structures;
	private final StudentService students;
	private final SchoolClassService classes;
	private final AcademicContext academicContext;
	private final Clock clock;

	public FeeRegisterService(InvoiceRepository invoices, FeeStructureService structures, StudentService students,
			SchoolClassService classes, AcademicContext academicContext, Clock clock) {
		this.invoices = invoices;
		this.structures = structures;
		this.students = students;
		this.classes = classes;
		this.academicContext = academicContext;
		this.clock = clock;
	}

	/**
	 * The register for one class, optionally narrowed to one section.
	 *
	 * <p>A class with no fee structure yet is not an error: the columns come back empty and the rows
	 * list the students with their individual charges, which is exactly what an office setting a new
	 * course up wants to see.
	 *
	 * @param sessionId defaults to the active session
	 * @param section   optional; omit for every section of the class
	 */
	public FeeRegisterResponse register(String sessionId, String classId, String section) {
		String session = hasText(sessionId) ? sessionId.trim() : academicContext.currentSessionId();
		SchoolClass schoolClass = classes.get(classId.trim());
		String normalizedSection = hasText(section) ? section.trim().toUpperCase(Locale.ROOT) : null;
		LocalDate today = LocalDate.now(clock);

		FeeStructure structure = structures.list(session, schoolClass.getId()).stream().findFirst().orElse(null);
		List<Installment> installments = structure == null ? List.of()
				: structure.installmentsOrEmpty().stream()
						// CUSTOM should never be on a structure, but a hand-written document could carry
						// one; it has no column here either way, because it is not everybody's charge.
						.filter(installment -> installment.resolvedKind() != InstallmentKind.CUSTOM)
						.sorted(COLUMN_ORDER)
						.toList();
		List<FeeRegisterResponse.Column> columns = installments.stream()
				.map(installment -> new FeeRegisterResponse.Column(installment.name(), installment.displayLabel(),
						installment.resolvedKind(), installment.dueDate()))
				.toList();

		List<StudentRef> roster = normalizedSection == null
				? students.activeInClass(session, schoolClass.getId())
				: students.activeInSection(schoolClass.getId(), normalizedSection);
		Map<String, List<Invoice>> byStudent = invoicesOf(roster, session);

		// One accumulator per column plus one for the charges, filled as the rows are built, so the
		// footer costs a second pass over nothing.
		Map<String, long[]> columnTotals = new LinkedHashMap<>();
		columns.forEach(column -> columnTotals.put(column.key(), new long[2]));
		long[] chargeTotals = new long[2];

		List<FeeRegisterResponse.Row> rows = new ArrayList<>(roster.size());
		for (StudentRef student : roster) {
			rows.add(rowFor(student, byStudent.getOrDefault(student.uniqueId(), List.of()),
					structure, columnTotals, chargeTotals, today));
		}

		return new FeeRegisterResponse(session, schoolClass.getId(), schoolClass.getName(), normalizedSection,
				columns, rows, footer(columns, columnTotals, chargeTotals));
	}

	// --- rows -------------------------------------------------------------------------------------

	/**
	 * One student's line, and their contribution to the column totals.
	 *
	 * <p>Cells are taken only from <em>this</em> structure's invoices. A student carrying an invoice
	 * from another structure — admitted into a different course mid-year, say — has no cell for it
	 * here, because a column means "this course's July" and theirs is not that. It still shows on their
	 * own ledger, which is where a per-student question is answered.
	 */
	private FeeRegisterResponse.Row rowFor(StudentRef student, List<Invoice> ledger, FeeStructure structure,
			Map<String, long[]> columnTotals, long[] chargeTotals, LocalDate today) {
		Map<String, FeeRegisterResponse.Cell> cells = new LinkedHashMap<>();
		List<FeeRegisterResponse.Item> charges = new ArrayList<>();
		long due = 0L;
		long paid = 0L;
		long chargeBalance = 0L;

		for (Invoice invoice : ledger) {
			if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
				// Out of the grid as it is out of every total: a withdrawn invoice is history, and a cell
				// for it would be a figure nobody is being asked for.
				continue;
			}
			long amount = invoice.getNetAmount();
			long balance = invoice.outstanding();
			FeeRegisterResponse.CellStatus status = statusOf(invoice, balance, today);

			if (invoice.resolvedKind() == InstallmentKind.CUSTOM) {
				charges.add(new FeeRegisterResponse.Item(invoice.getId(), invoice.displayLabel(), invoice.getNote(),
						invoice.getDueDate(), amount, balance, status));
				chargeBalance += balance;
				chargeTotals[0] += invoice.getPaidAmount();
				chargeTotals[1] += balance;
				due += amount;
				paid += invoice.getPaidAmount();
				continue;
			}
			long[] totals = structure != null && structure.getId().equals(invoice.getStructureId())
					? columnTotals.get(invoice.getInstallmentName())
					: null;
			if (totals == null) {
				// Another structure's invoice, or an installment that has since been removed from this
				// one. Left out of the row's totals as well as of the columns, so that every row adds up
				// to the footer and the sheet reconciles by eye. It is still on the student's own ledger,
				// which is where a per-student question belongs.
				continue;
			}
			cells.put(invoice.getInstallmentName(),
					new FeeRegisterResponse.Cell(invoice.getId(), amount, balance, status));
			totals[0] += invoice.getPaidAmount();
			totals[1] += balance;
			due += amount;
			paid += invoice.getPaidAmount();
		}

		charges.sort(Comparator.comparing(FeeRegisterResponse.Item::dueDate,
				Comparator.nullsLast(Comparator.naturalOrder())));
		return new FeeRegisterResponse.Row(student.uniqueId(), student.name(), student.rollNo(), cells,
				new FeeRegisterResponse.OtherCharges(charges.size(), chargeBalance, charges),
				new FeeRegisterResponse.Totals(due, paid, due - paid));
	}

	/** PAID, then OVERDUE for anything past its date with a balance, then PARTIAL or UNPAID. */
	private static FeeRegisterResponse.CellStatus statusOf(Invoice invoice, long balance, LocalDate today) {
		if (balance <= 0L) {
			return FeeRegisterResponse.CellStatus.PAID;
		}
		if (invoice.getDueDate() != null && today.isAfter(invoice.getDueDate())) {
			// Overdue wins over partial: a part-paid month that fell due last week is still late, and
			// late is what the office acts on.
			return FeeRegisterResponse.CellStatus.OVERDUE;
		}
		return invoice.getPaidAmount() > 0L
				? FeeRegisterResponse.CellStatus.PARTIAL
				: FeeRegisterResponse.CellStatus.UNPAID;
	}

	// --- internals --------------------------------------------------------------------------------

	/** Every invoice of this session belonging to the roster, grouped by student, in one read. */
	private Map<String, List<Invoice>> invoicesOf(List<StudentRef> roster, String sessionId) {
		if (roster.isEmpty()) {
			return Map.of();
		}
		List<String> uniqueIds = roster.stream().map(StudentRef::uniqueId).toList();
		return invoices.findByStudentUniqueIdIn(uniqueIds).stream()
				.filter(invoice -> sessionId.equals(invoice.getSessionId()))
				.collect(Collectors.groupingBy(Invoice::getStudentUniqueId));
	}

	private static FeeRegisterResponse.Footer footer(List<FeeRegisterResponse.Column> columns,
			Map<String, long[]> columnTotals, long[] chargeTotals) {
		List<FeeRegisterResponse.ColumnTotal> perColumn = columns.stream()
				.map(column -> {
					long[] totals = columnTotals.get(column.key());
					return new FeeRegisterResponse.ColumnTotal(column.key(), column.label(), totals[0], totals[1]);
				})
				.toList();
		long collected = perColumn.stream().mapToLong(FeeRegisterResponse.ColumnTotal::collected).sum()
				+ chargeTotals[0];
		long pending = perColumn.stream().mapToLong(FeeRegisterResponse.ColumnTotal::pending).sum()
				+ chargeTotals[1];
		return new FeeRegisterResponse.Footer(perColumn,
				new FeeRegisterResponse.ColumnTotal("otherCharges", "Other charges", chargeTotals[0],
						chargeTotals[1]),
				new FeeRegisterResponse.Totals(collected + pending, collected, pending));
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}

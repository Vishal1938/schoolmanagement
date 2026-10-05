package com.school.fees.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.school.academics.app.SchoolClassService;
import com.school.academics.domain.SchoolClass;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.BusinessRuleException;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ForbiddenException;
import com.school.common.exceptions.ValidationException;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.CurrentUser;
import com.school.common.security.Permission;
import com.school.fees.api.ApplyConcessionResponse;
import com.school.fees.api.FeeTotals;
import com.school.fees.api.GenerateInvoicesResponse;
import com.school.fees.api.InvoiceResponse;
import com.school.fees.api.StudentFeeStatusResponse;
import com.school.fees.api.StudentFeesResponse;
import com.school.fees.domain.Concession;
import com.school.fees.domain.FeeStructure;
import com.school.fees.domain.Installment;
import com.school.fees.domain.Invoice;
import com.school.fees.domain.InvoiceItem;
import com.school.fees.domain.InvoiceStatus;
import com.school.fees.domain.LateFineRule;
import com.school.fees.domain.StructureItem;
import com.school.fees.infra.InvoiceRepository;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Invoices: raising them from a structure, reading a student's ledger, and deriving the fee status.
 *
 * <p>Two rules run through all of it. <strong>The late fine is never stored</strong> — it is a
 * function of today's date, so it is computed on every read from the structure's live rule (see
 * {@link LateFine}) and returned as {@code lateFineDue}. And <strong>an invoice is a statement of
 * what was charged</strong>: its items, head names and concession are snapshots, so editing the
 * structure afterwards does not restate a bill a family has already been shown.
 */
@Service
public class InvoiceService {

	static final String AUDIT_ENTITY = "FeeInvoice";

	private final InvoiceRepository invoices;
	private final FeeStructureService structures;
	private final FeeHeadService heads;
	private final ConcessionService concessions;
	private final StudentService students;
	private final SchoolClassService classes;
	private final FeeStatusReader statuses;
	private final AuditService audit;
	private final Clock clock;

	public InvoiceService(InvoiceRepository invoices, FeeStructureService structures, FeeHeadService heads,
			ConcessionService concessions, StudentService students, SchoolClassService classes,
			FeeStatusReader statuses, AuditService audit, Clock clock) {
		this.invoices = invoices;
		this.structures = structures;
		this.heads = heads;
		this.concessions = concessions;
		this.students = students;
		this.classes = classes;
		this.statuses = statuses;
		this.audit = audit;
		this.clock = clock;
	}

	// --- generating -------------------------------------------------------------------------------

	/**
	 * Raises one invoice per ACTIVE student of the structure's class per installment, applying each
	 * student's concessions.
	 *
	 * <p><strong>Idempotent.</strong> The unique index on
	 * {@code (studentUniqueId, structureId, installmentName)} is what guarantees it: a row that
	 * already exists raises a duplicate key, which is counted as {@code skipped} rather than billing
	 * the family twice. Running it again after admitting a student therefore writes only the invoices
	 * that student is missing.
	 *
	 * <p>Deliberately <em>not</em> transactional. A duplicate key inside a MongoDB transaction aborts
	 * the whole transaction, which would turn the second run — the normal, expected one — into a
	 * failure instead of a no-op. Each insert stands on its own, and a partial run is safe precisely
	 * because re-running it finishes the job.
	 */
	public GenerateInvoicesResponse generate(String structureId) {
		FeeStructure structure = structures.get(structureId);
		List<Installment> installments = structure.getInstallments();
		if (installments == null || installments.isEmpty()) {
			throw new BusinessRuleException("This fee structure has no installments, so there is nothing to bill.");
		}
		List<StudentRef> roster = students.activeInClass(structure.getSessionId(), structure.getClassId());
		Map<String, String> headNames = heads.namesOf(headIdsOf(installments));
		Instant now = Instant.now(clock);

		int created = 0;
		int skipped = 0;
		for (StudentRef student : roster) {
			// Per student rather than per invoice: a student's concessions are the same for every
			// installment, and this is one read each rather than one per row.
			List<Concession> studentConcessions = concessions.forStudent(student.uniqueId(), structure.getSessionId());
			for (Installment installment : installments) {
				if (insert(structure, installment, student, headNames, studentConcessions, now)) {
					created++;
				}
				else {
					skipped++;
				}
			}
		}

		audit.record(AuditAction.FEE_INVOICES_GENERATED, AUDIT_ENTITY, structureId, null,
				Map.of("structureId", structureId, "classId", structure.getClassId(),
						"sessionId", structure.getSessionId(), "students", roster.size(),
						"installments", installments.size(), "created", created, "skipped", skipped));
		return new GenerateInvoicesResponse(created, skipped);
	}

	/** @return true when the invoice was written, false when it already existed */
	private boolean insert(FeeStructure structure, Installment installment, StudentRef student,
			Map<String, String> headNames, List<Concession> studentConcessions, Instant now) {
		List<InvoiceItem> items = installment.items().stream()
				.map(item -> new InvoiceItem(item.headId(), headNames.get(item.headId()), item.amount()))
				.toList();
		long gross = items.stream().mapToLong(InvoiceItem::amount).sum();
		long concessionAmount = Concessions.amountFor(items, studentConcessions);
		long netAmount = gross - concessionAmount;
		try {
			invoices.insert(Invoice.builder()
					.studentUniqueId(student.uniqueId())
					.classId(structure.getClassId())
					.sessionId(structure.getSessionId())
					.structureId(structure.getId())
					.installmentName(installment.name())
					.dueDate(installment.dueDate())
					.items(items)
					.concessionAmount(concessionAmount)
					.netAmount(netAmount)
					.paidAmount(0L)
					.lateFinePaid(0L)
					// A full waiver bills nothing, so it is settled the moment it is raised rather than
					// sitting as an UNPAID invoice for zero that every report has to explain.
					.status(statusFor(0L, netAmount))
					.createdAt(now)
					.updatedAt(now)
					.build());
			return true;
		}
		catch (DuplicateKeyException ex) {
			return false;
		}
	}

	// --- reading ----------------------------------------------------------------------------------

	/**
	 * One student's whole ledger plus their totals.
	 *
	 * <p>Who may read it is an object-level rule — an admin, or that student — so it is checked here
	 * rather than in an annotation. A teacher holding {@code FEE_READ_STATUS} is refused: they get
	 * {@code /status}, which has no field for an amount (CLAUDE.md rule 2).
	 */
	public StudentFeesResponse ledgerFor(String uniqueId) {
		AuthPrincipal caller = CurrentUser.require();
		StudentRef student = students.requireRef(uniqueId);
		if (!caller.permissions().contains(Permission.FEE_READ_FULL) && !isSelf(caller, student.uniqueId())) {
			throw new ForbiddenException("You may only read your own fee records");
		}

		List<Invoice> ledger = invoices.findByStudentUniqueIdOrderByDueDateAsc(student.uniqueId());
		Map<String, LateFineRule> rules = structures.lateFineRulesOf(FeeStatusReader.structureIdsOf(ledger));
		LocalDate today = statuses.today();

		List<InvoiceResponse> rows = new ArrayList<>(ledger.size());
		long totalNet = 0L;
		long totalPaid = 0L;
		long totalLateFineDue = 0L;
		for (Invoice invoice : ledger) {
			LateFineRule rule = rules.get(invoice.getStructureId());
			long lateFineDue = LateFine.dueOn(invoice, rule, today);
			rows.add(InvoiceResponse.of(invoice, lateFineDue, LateFine.isOverdue(invoice, rule, today)));
			if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
				// Still listed — a withdrawn invoice is part of the history — but out of every total.
				continue;
			}
			totalNet += invoice.getNetAmount();
			totalPaid += invoice.getPaidAmount();
			totalLateFineDue += lateFineDue;
		}

		FeeTotals totals = new FeeTotals(totalNet, totalPaid, totalLateFineDue,
				totalNet - totalPaid + totalLateFineDue);
		return new StudentFeesResponse(student.uniqueId(), student.name(), student.classId(),
				classes.findById(student.classId()).map(SchoolClass::getName).orElse(null), student.section(),
				// The ledger and the rules are already loaded, so this costs no further queries.
				statuses.statusOf(ledger, rules, today), rows, totals);
	}

	/**
	 * The derived status, which is all a teacher or a student-list row ever sees.
	 *
	 * <p>Who may read it: an admin or a teacher for anybody, a student only for themselves. The
	 * endpoint itself is gated on {@code FEE_READ_STATUS}, which keeps staff out entirely.
	 */
	public StudentFeeStatusResponse statusForCaller(String uniqueId) {
		AuthPrincipal caller = CurrentUser.require();
		StudentRef student = students.requireRef(uniqueId);
		boolean mayReadAnyone = caller.permissions().contains(Permission.FEE_READ_FULL)
				|| caller.permissions().contains(Permission.STUDENT_READ_BASIC);
		if (!mayReadAnyone && !isSelf(caller, student.uniqueId())) {
			throw new ForbiddenException("You may only read your own fee status");
		}
		// The resolved uniqueId, not the one in the URL, so the response echoes the canonical form.
		return new StudentFeeStatusResponse(student.uniqueId(), statuses.statusFor(student.uniqueId()));
	}

	// --- quoting ----------------------------------------------------------------------------------

	/**
	 * What the named invoices come to right now: outstanding principal plus the live late fine.
	 *
	 * <p>This is what makes the online flow safe. The client sends invoice ids and no amount at all,
	 * and the figure the gateway order is created for comes from here (CLAUDE.md rule 4).
	 *
	 * <p>Strict, because this is a student choosing what to pay: every id must exist, must be theirs,
	 * and must still be {@code UNPAID} or {@code PARTIAL}. A settled or cancelled invoice is refused
	 * rather than quietly dropped — a checkout for less than the family selected is worse than an
	 * error telling them the list has moved on.
	 *
	 * @throws ValidationException   if an invoice is unknown, another student's, or not payable
	 * @throws BusinessRuleException if there is nothing left to pay
	 */
	public FeeQuote quoteForPayment(String studentUniqueId, List<String> invoiceIds) {
		List<Invoice> selected = payableInvoices(studentUniqueId, invoiceIds);
		List<FieldViolation> violations = new ArrayList<>();
		for (Invoice invoice : selected) {
			if (invoice.getStatus() != InvoiceStatus.UNPAID && invoice.getStatus() != InvoiceStatus.PARTIAL) {
				violations.add(new FieldViolation("invoiceIds",
						"invoice " + invoice.getId() + " is " + invoice.getStatus() + ", so there is nothing to pay"));
			}
		}
		if (!violations.isEmpty()) {
			throw new ValidationException("Those invoices cannot be paid", violations);
		}
		long due = dueTotal(selected);
		if (due <= 0L) {
			throw new BusinessRuleException("There is nothing left to pay on the invoices you selected.");
		}
		return new FeeQuote(selected.stream().map(Invoice::getId).toList(), due);
	}

	/**
	 * The same figure, computed again for a gateway payment that is coming back to be settled.
	 *
	 * <p>Lenient where {@link #quoteForPayment} is strict, and deliberately so. Minutes have passed:
	 * the late fine may have grown by a day, a clerk may have taken the same invoices at the counter,
	 * an invoice may have been cancelled. The money is already at the gateway, so there is nothing
	 * useful to refuse — anything no longer payable is dropped and the rest is quoted. Whatever cannot
	 * be allocated is recorded against the payment rather than lost.
	 */
	public FeeQuote requoteForSettlement(String studentUniqueId, List<String> invoiceIds) {
		if (invoiceIds == null || invoiceIds.isEmpty()) {
			return new FeeQuote(List.of(), 0L);
		}
		List<Invoice> open = invoices.findAllByIdIn(invoiceIds).stream()
				.filter(invoice -> studentUniqueId.equals(invoice.getStudentUniqueId()))
				.filter(invoice -> invoice.getStatus() != InvoiceStatus.CANCELLED)
				.toList();
		return new FeeQuote(open.stream().map(Invoice::getId).toList(), dueTotal(open));
	}

	/**
	 * Outstanding principal plus today's late fine across these invoices, in paise. The single
	 * definition of "what these come to", shared by both quotes and by the allocation's own ceiling.
	 */
	private long dueTotal(List<Invoice> selected) {
		Map<String, LateFineRule> rules = structures.lateFineRulesOf(FeeStatusReader.structureIdsOf(selected));
		LocalDate today = statuses.today();
		return selected.stream()
				.mapToLong(invoice -> invoice.outstanding()
						+ LateFine.dueOn(invoice, rules.get(invoice.getStructureId()), today))
				.sum();
	}

	// --- taking money -----------------------------------------------------------------------------

	/**
	 * Applies a payment to the named invoices and returns what landed where.
	 *
	 * <p>This is the <strong>only</strong> way money reaches an invoice. The payment module owns the
	 * receipt, the mode and the payer; it does not own the invoice, so it calls this rather than
	 * writing the collection itself (CLAUDE.md module rule). The caller is expected to be inside a
	 * transaction — these writes and the payment document have to stand or fall together — and this
	 * method joins it rather than starting its own.
	 *
	 * <p><strong>The amount is validated, never trusted</strong> (CLAUDE.md rule 4): it is checked
	 * against what these invoices actually come to, computed here from the stored principal and the
	 * live late fine. Anything over that is refused rather than left as a credit, because an
	 * overpayment with nowhere to go is a reconciliation problem later.
	 *
	 * <p>Allocation runs in <strong>due-date order, oldest first, and within an invoice the late fine
	 * is settled before the principal</strong>. Paying the fine first is what stops a part payment
	 * from leaving a fine that keeps growing on an invoice the family thought they had dealt with.
	 * A short payment is allowed and leaves the invoice {@code PARTIAL}.
	 *
	 * @param amount what was actually taken, in paise
	 * @throws ValidationException   if an invoice is not this student's, or is not payable
	 * @throws BusinessRuleException if {@code amount} exceeds what these invoices come to
	 */
	public List<InvoiceAllocation> allocate(String studentUniqueId, List<String> invoiceIds, long amount) {
		List<Invoice> selected = payableInvoices(studentUniqueId, invoiceIds);
		Map<String, LateFineRule> rules = structures.lateFineRulesOf(FeeStatusReader.structureIdsOf(selected));
		LocalDate today = statuses.today();

		long totalDue = dueTotal(selected);
		if (amount > totalDue) {
			throw new BusinessRuleException("That is more than these invoices come to. They total "
					+ rupees(totalDue) + " including any late fine, and " + rupees(amount) + " was offered. "
					+ "Select more invoices, or take the smaller amount.");
		}

		Instant now = Instant.now(clock);
		List<InvoiceAllocation> allocations = new ArrayList<>();
		long remaining = amount;
		// Oldest first: the debt a family has carried longest is the one a counter payment clears.
		List<Invoice> oldestFirst = selected.stream()
				.sorted(Comparator.comparing(Invoice::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();
		for (Invoice before : oldestFirst) {
			if (remaining <= 0L) {
				break;
			}
			long fineDue = LateFine.dueOn(before, rules.get(before.getStructureId()), today);
			long fine = Math.min(remaining, fineDue);
			remaining -= fine;
			long principal = Math.min(remaining, before.outstanding());
			remaining -= principal;
			if (fine == 0L && principal == 0L) {
				continue;
			}

			long paidAmount = before.getPaidAmount() + principal;
			Invoice saved = invoices.save(before.toBuilder()
					.paidAmount(paidAmount)
					// Freezing the fine that was collected. The fine still *owing* stays a calculation;
					// what was taken is a fact, and it is what stops it being charged twice.
					.lateFinePaid(before.getLateFinePaid() + fine)
					// Derived from the principal alone, so the invoice status and the student's fee
					// status can never disagree. A payment that covered only the fine leaves the
					// invoice UNPAID, which is the honest reading: none of the fee has been paid.
					.status(statusFor(paidAmount, before.getNetAmount()))
					.updatedAt(now)
					.build());
			audit.record(AuditAction.FEE_PAYMENT_APPLIED, AUDIT_ENTITY, saved.getId(), before, saved);
			allocations.add(new InvoiceAllocation(saved.getId(), saved.getInstallmentName(), saved.getDueDate(),
					principal, fine, headShares(saved, principal)));
		}
		return allocations;
	}

	/** The named invoices, checked to be this student's and still worth paying. */
	private List<Invoice> payableInvoices(String studentUniqueId, List<String> invoiceIds) {
		List<Invoice> found = invoices.findAllByIdIn(invoiceIds);
		Map<String, Invoice> byId = found.stream().collect(Collectors.toMap(Invoice::getId, invoice -> invoice));
		List<FieldViolation> violations = new ArrayList<>();
		for (int i = 0; i < invoiceIds.size(); i++) {
			String id = invoiceIds.get(i);
			String field = "invoiceIds[" + i + "]";
			Invoice invoice = byId.get(id);
			if (invoice == null) {
				violations.add(new FieldViolation(field, "no invoice with id " + id));
			}
			else if (!invoice.getStudentUniqueId().equals(studentUniqueId)) {
				// Checked rather than assumed: without it, a counter clerk could settle one family's
				// invoice from another's payment by pasting the wrong id.
				violations.add(new FieldViolation(field, "belongs to another student"));
			}
			else if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
				violations.add(new FieldViolation(field, "has been cancelled"));
			}
		}
		if (!violations.isEmpty()) {
			throw new ValidationException("Those invoices cannot be paid", violations);
		}
		if (found.isEmpty()) {
			throw new ValidationException("No invoices were selected",
					List.of(new FieldViolation("invoiceIds", "must name at least one invoice")));
		}
		return found;
	}

	/**
	 * Splits the principal collected across the invoice's heads, in proportion to what each head was
	 * charged.
	 *
	 * <p>Proportional rather than head-by-head-until-exhausted, so a part payment of a bill does not
	 * read as "Tuition paid in full, Transport untouched" when the family paid half of everything.
	 * Integer division rounds each share down and the remainder goes on the last head, so the shares
	 * always sum to exactly what was taken — a report that does not reconcile is worse than one that
	 * puts a few paise in the wrong column.
	 */
	private static List<InvoiceAllocation.HeadShare> headShares(Invoice invoice, long principal) {
		List<InvoiceItem> items = invoice.getItems();
		if (items == null || items.isEmpty() || principal <= 0L) {
			return List.of();
		}
		long gross = invoice.grossAmount();
		if (gross <= 0L) {
			return List.of();
		}
		List<InvoiceAllocation.HeadShare> shares = new ArrayList<>(items.size());
		long allocated = 0L;
		for (int i = 0; i < items.size(); i++) {
			InvoiceItem item = items.get(i);
			boolean last = i == items.size() - 1;
			long share = last ? principal - allocated : principal * item.amount() / gross;
			allocated += share;
			shares.add(new InvoiceAllocation.HeadShare(item.headId(), item.headName(), share));
		}
		return shares;
	}

	/**
	 * An invoice's status from the principal alone. A zero-net invoice — a full waiver — is settled the
	 * moment it exists, which is also what {@code generate-invoices} relies on.
	 */
	private static InvoiceStatus statusFor(long paidAmount, long netAmount) {
		if (paidAmount >= netAmount) {
			return InvoiceStatus.PAID;
		}
		return paidAmount > 0L ? InvoiceStatus.PARTIAL : InvoiceStatus.UNPAID;
	}

	/** For error messages only. Figures in the API itself are always paise. */
	private static String rupees(long paise) {
		return "₹" + String.format(Locale.ENGLISH, "%,.2f", paise / 100.0d);
	}

	// --- recalculating ----------------------------------------------------------------------------

	/**
	 * Pulls a concession back over the student's invoices that nothing has been paid against.
	 *
	 * <p>Only {@code UNPAID} invoices of that concession's session are touched. A PARTIAL or PAID
	 * invoice is left alone deliberately: restating a bill a family has already paid into would
	 * leave the receipt and the invoice disagreeing, and that is a credit note rather than an edit.
	 * Every rewritten invoice gets its own audit entry with the before and after.
	 *
	 * <p>The figure is recomputed from <em>all</em> of the student's concessions for that session, not
	 * just this one, so applying two concessions in either order gives the same answer.
	 */
	public ApplyConcessionResponse applyConcession(String concessionId) {
		Concession concession = concessions.get(concessionId);
		String studentUniqueId = concession.getStudentUniqueId();
		String sessionId = concession.getSessionId();
		List<Concession> all = concessions.forStudent(studentUniqueId, sessionId);
		List<Invoice> unpaid = invoices.findByStudentUniqueIdAndSessionIdAndStatus(
				studentUniqueId, sessionId, InvoiceStatus.UNPAID);
		Instant now = Instant.now(clock);

		int recalculated = 0;
		int skipped = 0;
		for (Invoice before : unpaid) {
			long concessionAmount = Concessions.amountFor(before.getItems(), all);
			if (concessionAmount == before.getConcessionAmount()) {
				skipped++;
				continue;
			}
			long netAmount = before.grossAmount() - concessionAmount;
			Invoice saved = invoices.save(before.toBuilder()
					.concessionAmount(concessionAmount)
					.netAmount(netAmount)
					.status(statusFor(before.getPaidAmount(), netAmount))
					.updatedAt(now)
					.build());
			audit.record(AuditAction.FEE_INVOICE_RECALCULATED, AUDIT_ENTITY, saved.getId(), before, saved);
			recalculated++;
		}

		audit.record(AuditAction.FEE_CONCESSION_APPLIED, ConcessionService.AUDIT_ENTITY, concessionId, null,
				Map.of("studentUniqueId", studentUniqueId, "sessionId", sessionId,
						"recalculated", recalculated, "skipped", skipped));
		return new ApplyConcessionResponse(recalculated, skipped);
	}

	// --- internals --------------------------------------------------------------------------------

	private static boolean isSelf(AuthPrincipal caller, String studentUniqueId) {
		return caller.uniqueId() != null && caller.uniqueId().equals(studentUniqueId);
	}

	private static Set<String> headIdsOf(List<Installment> installments) {
		return installments.stream()
				.filter(installment -> installment.items() != null)
				.flatMap(installment -> installment.items().stream())
				.map(StructureItem::headId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}
}

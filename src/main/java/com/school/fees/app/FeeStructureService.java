package com.school.fees.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.school.academics.app.AcademicContext;
import com.school.academics.app.AcademicSessionService;
import com.school.academics.app.SchoolClassService;
import com.school.academics.domain.SchoolClass;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.BusinessRuleException;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ValidationException;
import com.school.fees.api.FeeStructureRequest;
import com.school.fees.api.MonthlyFeeSetupRequest;
import com.school.fees.api.OneTimeFeeRequest;
import com.school.fees.domain.FeeHead;
import com.school.fees.domain.FeeStructure;
import com.school.fees.domain.Installment;
import com.school.fees.domain.InstallmentKind;
import com.school.fees.domain.Invoice;
import com.school.fees.domain.LateFineRule;
import com.school.fees.domain.StructureItem;
import com.school.fees.infra.FeeStructureRepository;
import com.school.fees.infra.InvoiceRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Fee structures: what one class pays in one session, and the rule for paying late.
 *
 * <p>Everything in a request is validated before anything is written, so one bad head id changes
 * nothing and reports every problem at once — the same stance as the exam schedule in B9.
 *
 * <p>This service reads {@code InvoiceRepository} directly. That is within the module, and it is
 * here rather than behind {@code InvoiceService} to keep the two services acyclic: invoices need the
 * structure's late-fine rule, so the dependency only runs one way.
 */
@Service
public class FeeStructureService {

	private static final String AUDIT_ENTITY = "FeeStructure";

	/** How a monthly installment is named: {@code "Jul 2026"}. English, because an id is not a label. */
	private static final DateTimeFormatter MONTH_NAME = DateTimeFormatter.ofPattern("MMM uuuu", Locale.ENGLISH);

	private final FeeStructureRepository structures;
	private final InvoiceRepository invoices;
	private final FeeHeadService headService;
	private final SchoolClassService classes;
	private final AcademicContext academicContext;
	private final AcademicSessionService sessions;
	private final AuditService audit;
	private final Clock clock;

	public FeeStructureService(FeeStructureRepository structures, InvoiceRepository invoices,
			FeeHeadService headService, SchoolClassService classes, AcademicContext academicContext,
			AcademicSessionService sessions, AuditService audit, Clock clock) {
		this.structures = structures;
		this.invoices = invoices;
		this.headService = headService;
		this.classes = classes;
		this.academicContext = academicContext;
		this.sessions = sessions;
		this.audit = audit;
		this.clock = clock;
	}

	// --- reading ----------------------------------------------------------------------------------

	/** Structures of a session, defaulting to the active one, optionally for one class. */
	public List<FeeStructure> list(String sessionId, String classId) {
		String session = hasText(sessionId) ? sessionId.trim() : academicContext.currentSessionId();
		if (hasText(classId)) {
			return structures.findBySessionIdAndClassId(session, classId.trim())
					.map(List::of)
					.orElseGet(List::of);
		}
		return structures.findBySessionIdOrderByClassIdAsc(session);
	}

	public FeeStructure get(String id) {
		return structures.findById(id).orElseThrow(() -> NotFoundException.of("Fee structure", id));
	}

	/** Structure id to its late-fine rule, for a set of invoices, in one read. Null values are kept out. */
	public Map<String, LateFineRule> lateFineRulesOf(Collection<String> structureIds) {
		if (structureIds == null || structureIds.isEmpty()) {
			return Map.of();
		}
		Map<String, LateFineRule> rules = new LinkedHashMap<>();
		structures.findAllByIdIn(structureIds).forEach(structure -> {
			if (structure.getLateFine() != null) {
				rules.put(structure.getId(), structure.getLateFine());
			}
		});
		return rules;
	}

	/** Head id to name across every installment of this structure, for the response. */
	public Map<String, String> headNamesOf(List<FeeStructure> forStructures) {
		return headService.namesOf(headIdsOf(forStructures));
	}

	public String classNameOf(FeeStructure structure) {
		return classes.findById(structure.getClassId()).map(SchoolClass::getName).orElse(null);
	}

	// --- writing ----------------------------------------------------------------------------------

	public FeeStructure create(FeeStructureRequest request) {
		String sessionId = academicContext.currentSessionId();
		SchoolClass schoolClass = validatedClass(request.classId());
		List<Installment> installments = validatedInstallments(request, schoolClass);
		if (structures.findBySessionIdAndClassId(sessionId, schoolClass.getId()).isPresent()) {
			throw new ConflictException(schoolClass.getName() + " already has a fee structure for this session. "
					+ "Edit that one instead — a class can only have one.");
		}
		Instant now = Instant.now(clock);
		FeeStructure saved = insert(FeeStructure.builder()
				.sessionId(sessionId)
				.classId(schoolClass.getId())
				.installments(installments)
				.lateFine(toRule(request.lateFine()))
				.createdAt(now)
				.updatedAt(now)
				.build());
		audit.record(AuditAction.FEE_STRUCTURE_CREATED, AUDIT_ENTITY, saved.getId(), null, saved);
		return saved;
	}

	/**
	 * Replaces the installments and the late-fine rule.
	 *
	 * <p>Two things are refused. The {@code classId} cannot change, because invoices generated from
	 * this structure name the old class. And an installment that already has invoices cannot be
	 * renamed or dropped: the installment name is part of the idempotency key, so a rename would make
	 * {@code generate-invoices} bill those students a second time under the new name.
	 *
	 * <p>Amounts, due dates and the late-fine rule <em>are</em> editable. Invoices already generated
	 * keep the amounts they were issued with — they are statements of what was charged — while the
	 * late-fine rule is read live, so correcting it corrects every invoice at once.
	 */
	public FeeStructure update(String id, FeeStructureRequest request) {
		FeeStructure before = get(id);
		if (!before.getClassId().equals(request.classId().trim())) {
			throw new BusinessRuleException("A fee structure cannot be moved to another class: the invoices "
					+ "generated from it name the class it was created for. Create a structure on the other "
					+ "class instead.");
		}
		List<Installment> installments = validatedInstallments(request, validatedClass(request.classId()));
		requireInstallmentsWithInvoicesKept(id, installments);

		FeeStructure saved = structures.save(before.toBuilder()
				.installments(installments)
				.lateFine(toRule(request.lateFine()))
				.updatedAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.FEE_STRUCTURE_UPDATED, AUDIT_ENTITY, id, before, saved);
		return saved;
	}

	// --- monthly plans ----------------------------------------------------------------------------

	/**
	 * Writes a run of monthly installments into the structure for one session and class, creating the
	 * structure if there is none.
	 *
	 * <p>Three outcomes per month, and the counts are returned so an admin can see which happened. A
	 * month the structure does not have is <strong>added</strong>. A month it has, with no invoices
	 * raised from it, has this head's amount <strong>rewritten</strong> — other heads already on that
	 * month are left alone, because this endpoint is about one head and must not quietly drop the
	 * transport fee somebody added by hand. A month that already has invoices is
	 * <strong>skipped</strong> entirely: an invoice states what a family was charged, and the way to
	 * change one is a concession or a credit, not an edit.
	 *
	 * <p>Nothing is generated here. The invoices follow from the ordinary
	 * {@code POST /fees/structures/{id}/generate-invoices}, which is idempotent and will raise exactly
	 * the months it has not raised yet.
	 */
	public MonthlyPlanResult setUpMonthly(MonthlyFeeSetupRequest request) {
		String sessionId = resolvedSessionId(request.sessionId());
		SchoolClass schoolClass = validatedClass(request.classId());
		FeeHead head = validatedActiveHead(request.headId());
		List<FieldViolation> violations = new ArrayList<>();
		Map<String, Long> overrides = validatedSectionAmounts(request.sectionAmounts(), schoolClass,
				"sectionAmounts", violations);
		if (!violations.isEmpty()) {
			throw new ValidationException("The monthly fee plan could not be saved", violations);
		}

		FeeStructure before = structures.findBySessionIdAndClassId(sessionId, schoolClass.getId()).orElse(null);
		Set<String> billed = before == null ? Set.of() : installmentNamesWithInvoices(before.getId());
		// Keyed by name so an existing month is found in one lookup; insertion order is irrelevant,
		// because every read sorts by kind and due date anyway.
		Map<String, Installment> byName = new LinkedHashMap<>();
		if (before != null) {
			before.installmentsOrEmpty().forEach(installment -> byName.put(installment.name(), installment));
		}

		int created = 0;
		int updated = 0;
		int skipped = 0;
		YearMonth month = request.startMonth();
		for (int i = 0; i < request.monthsOrDefault(); i++, month = month.plusMonths(1)) {
			String name = MONTH_NAME.format(month);
			LocalDate dueDate = dueDateIn(month, request.dueDayOrDefault());
			Installment existing = byName.get(name);
			if (existing == null) {
				byName.put(name, new Installment(name, dueDate,
						List.of(new StructureItem(head.getId(), request.monthlyAmount(), overrides)),
						InstallmentKind.MONTHLY, null));
				created++;
			}
			else if (billed.contains(name)) {
				skipped++;
			}
			else {
				byName.put(name, withHeadAmount(existing, head.getId(), request.monthlyAmount(), overrides, dueDate));
				updated++;
			}
		}

		List<Installment> installments = List.copyOf(byName.values());
		Instant now = Instant.now(clock);
		FeeStructure saved;
		if (before == null) {
			saved = insert(FeeStructure.builder()
					.sessionId(sessionId)
					.classId(schoolClass.getId())
					.installments(installments)
					.createdAt(now)
					.updatedAt(now)
					.build());
			audit.record(AuditAction.FEE_STRUCTURE_CREATED, AUDIT_ENTITY, saved.getId(), null, saved);
		}
		else {
			saved = structures.save(before.toBuilder().installments(installments).updatedAt(now).build());
			audit.record(AuditAction.FEE_STRUCTURE_UPDATED, AUDIT_ENTITY, saved.getId(), before, saved);
		}
		return new MonthlyPlanResult(saved, created, updated, skipped);
	}

	/** The structure after a monthly plan was written, with what happened to each month. */
	public record MonthlyPlanResult(FeeStructure structure, int created, int updated, int skipped) {
	}

	// --- course-wide one-off fees -----------------------------------------------------------------

	/**
	 * Adds a {@code ONE_TIME} installment to a structure — an admission fee, a laboratory deposit.
	 *
	 * <p>Nothing is generated here either: running {@code generate-invoices} again raises this one for
	 * every student on the roll and nothing else, because every month already has its invoices and the
	 * unique index counts those as skipped.
	 */
	public FeeStructure addOneTimeFee(String structureId, OneTimeFeeRequest request) {
		FeeStructure before = get(structureId);
		String name = request.name().trim();
		if (before.installmentNamed(name).isPresent()) {
			throw new ConflictException("This structure already has an installment called \"" + name
					+ "\". Installment names have to be unique within a structure, because an invoice names "
					+ "the installment it came from.");
		}
		Installment added = validatedOneTimeFee(before, request, name);
		List<Installment> installments = new ArrayList<>(before.installmentsOrEmpty());
		installments.add(added);
		return saveInstallments(before, installments);
	}

	/**
	 * Replaces a one-off fee's head, amount, overrides and due date.
	 *
	 * <p>Renaming is allowed only while the fee has no invoices, and changing the amount of one that
	 * has is allowed deliberately: the structure is what the <em>next</em> student admitted will be
	 * billed, and invoices already raised keep the amounts they were issued with, exactly as
	 * {@link #update} does. A rename is different in kind — the name is part of the idempotency key, so
	 * it would raise the same fee again under the new name.
	 */
	public FeeStructure updateOneTimeFee(String structureId, String installmentName, OneTimeFeeRequest request) {
		FeeStructure before = get(structureId);
		Installment existing = requireOneTimeFee(before, installmentName);
		String name = request.name().trim();
		if (!name.equals(existing.name()) && !invoicesFor(before.getId(), existing.name()).isEmpty()) {
			throw new BusinessRuleException("\"" + existing.name() + "\" already has invoices and cannot be "
					+ "renamed. The installment name is part of what makes invoice generation idempotent, so "
					+ "renaming it would raise this fee a second time under the new name.");
		}
		if (!name.equals(existing.name()) && before.installmentNamed(name).isPresent()) {
			throw new ConflictException("This structure already has an installment called \"" + name + "\"");
		}

		Installment replacement = validatedOneTimeFee(before, request, name);
		List<Installment> installments = before.installmentsOrEmpty().stream()
				.map(installment -> installment.name().equals(existing.name()) ? replacement : installment)
				.toList();
		return saveInstallments(before, new ArrayList<>(installments));
	}

	/**
	 * Removes a one-off fee and the invoices raised from it.
	 *
	 * <p>Refused outright once a single rupee has been taken against any of those invoices. Invoices are
	 * otherwise never deleted — {@code CANCELLED} is how one stops counting — but a fee added by
	 * mistake and billed to a class the same afternoon is the one case where there is nothing to keep:
	 * no receipt points at it, no report has reported it, and leaving two hundred cancelled invoices
	 * behind would make every register harder to read for no gain.
	 */
	public FeeStructure deleteOneTimeFee(String structureId, String installmentName) {
		FeeStructure before = get(structureId);
		Installment existing = requireOneTimeFee(before, installmentName);
		List<Invoice> raised = invoicesFor(before.getId(), existing.name());
		List<Invoice> paid = raised.stream().filter(invoice -> !invoice.untouched()).toList();
		if (!paid.isEmpty()) {
			throw new BusinessRuleException("\"" + existing.name() + "\" cannot be deleted: " + paid.size()
					+ " of its " + raised.size() + " invoices have had money taken against them, and a receipt "
					+ "has been issued for it. Set the amount to 0 for students not yet billed, or raise a "
					+ "concession, rather than deleting the fee.");
		}

		List<Installment> installments = before.installmentsOrEmpty().stream()
				.filter(installment -> !installment.name().equals(existing.name()))
				.collect(Collectors.toCollection(ArrayList::new));
		FeeStructure saved = saveInstallments(before, installments);
		invoices.deleteAll(raised);
		audit.record(AuditAction.FEE_ONE_TIME_FEE_DELETED, AUDIT_ENTITY, structureId, existing,
				Map.of("installmentName", existing.name(), "invoicesDeleted", raised.size()));
		return saved;
	}

	// --- plan internals ---------------------------------------------------------------------------

	/** The session to write against: the one asked for, checked to exist, or the active one. */
	private String resolvedSessionId(String sessionId) {
		if (!hasText(sessionId)) {
			return academicContext.currentSessionId();
		}
		// Checked rather than taken on trust: a structure keyed to a session that does not exist would
		// never be found by invoice generation, and the mistake would only show up as "no invoices".
		return sessions.get(sessionId.trim()).getId();
	}

	/** The named day of that month, or its last day when the month is shorter. */
	private static LocalDate dueDateIn(YearMonth month, int dueDay) {
		return month.atDay(Math.min(dueDay, month.lengthOfMonth()));
	}

	/** The installment with this head's amount set, every other head kept, and the due date refreshed. */
	private static Installment withHeadAmount(Installment installment, String headId, long amount,
			Map<String, Long> overrides, LocalDate dueDate) {
		List<StructureItem> items = new ArrayList<>();
		boolean replaced = false;
		for (StructureItem item : installment.items() == null ? List.<StructureItem>of() : installment.items()) {
			if (item.headId().equals(headId)) {
				items.add(new StructureItem(headId, amount, overrides));
				replaced = true;
			}
			else {
				items.add(item);
			}
		}
		if (!replaced) {
			items.add(new StructureItem(headId, amount, overrides));
		}
		return new Installment(installment.name(), dueDate, items, InstallmentKind.MONTHLY, installment.label());
	}

	/** Validates a one-off fee request against the structure's class and returns the installment to store. */
	private Installment validatedOneTimeFee(FeeStructure structure, OneTimeFeeRequest request, String name) {
		SchoolClass schoolClass = validatedClass(structure.getClassId());
		FeeHead head = validatedActiveHead(request.headId());
		List<FieldViolation> violations = new ArrayList<>();
		Map<String, Long> overrides = validatedSectionAmounts(request.sectionAmounts(), schoolClass,
				"sectionAmounts", violations);
		if (!violations.isEmpty()) {
			throw new ValidationException("That fee could not be saved", violations);
		}
		return new Installment(name, request.dueDate(),
				List.of(new StructureItem(head.getId(), request.amount(), overrides)),
				InstallmentKind.ONE_TIME, name);
	}

	/** The named installment, refused if it is a month rather than a one-off fee. */
	private static Installment requireOneTimeFee(FeeStructure structure, String installmentName) {
		Installment installment = structure.installmentNamed(installmentName)
				.orElseThrow(() -> new NotFoundException("This structure has no fee called \"" + installmentName
						+ "\""));
		if (installment.resolvedKind() != InstallmentKind.ONE_TIME) {
			throw new BusinessRuleException("\"" + installmentName + "\" is a " + installment.resolvedKind()
					+ " installment, not a one-off fee. Monthly installments are managed through "
					+ "POST /fees/structures/monthly or PUT /fees/structures/{id}.");
		}
		return installment;
	}

	private FeeStructure saveInstallments(FeeStructure before, List<Installment> installments) {
		FeeStructure saved = structures.save(before.toBuilder()
				.installments(List.copyOf(installments))
				.updatedAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.FEE_STRUCTURE_UPDATED, AUDIT_ENTITY, saved.getId(), before, saved);
		return saved;
	}

	private List<Invoice> invoicesFor(String structureId, String installmentName) {
		return invoices.findByStructureIdAndInstallmentName(structureId, installmentName);
	}

	private Set<String> installmentNamesWithInvoices(String structureId) {
		return invoices.findByStructureId(structureId).stream()
				.map(Invoice::getInstallmentName)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/** A head that exists and has not been retired; a retired one on a new charge is a stale form. */
	private FeeHead validatedActiveHead(String headId) {
		FeeHead head = headService.findAllByIdIn(Set.of(headId.trim())).stream().findFirst()
				.orElseThrow(() -> new ValidationException("That fee head does not exist",
						List.of(new FieldViolation("headId", "no fee head with id " + headId))));
		if (!head.isActive()) {
			throw new ValidationException("That fee head has been retired",
					List.of(new FieldViolation("headId", head.getName() + " is no longer active")));
		}
		return head;
	}

	// --- validation -------------------------------------------------------------------------------

	private SchoolClass validatedClass(String classId) {
		return classes.findById(classId.trim()).orElseThrow(() ->
				new ValidationException("The fee structure does not point at a real class",
						List.of(new FieldViolation("classId", "no class with id " + classId))));
	}

	/**
	 * Installment names unique within the structure, every head id real and still active, no head
	 * charged twice in one installment, and every section override naming a section the class has.
	 */
	private List<Installment> validatedInstallments(FeeStructureRequest request, SchoolClass schoolClass) {
		List<FieldViolation> violations = new ArrayList<>();
		Set<String> seenNames = new LinkedHashSet<>();
		List<Installment> result = new ArrayList<>();

		Map<String, FeeHead> headsById = activeHeadsFor(request, violations);

		List<FeeStructureRequest.Installment> requested = request.installments();
		for (int i = 0; i < requested.size(); i++) {
			FeeStructureRequest.Installment installment = requested.get(i);
			String field = "installments[" + i + "]";
			String name = installment.name().trim();
			if (!seenNames.add(name)) {
				violations.add(new FieldViolation(field + ".name", "is listed twice"));
				continue;
			}
			if (installment.kind() == InstallmentKind.CUSTOM) {
				// A CUSTOM charge is raised against named students and has no installment behind it, so
				// putting one on a structure would bill the whole class for somebody's lost library book.
				violations.add(new FieldViolation(field + ".kind",
						"must be MONTHLY or ONE_TIME; a charge for named students is POST /fees/charges"));
				continue;
			}
			Set<String> seenHeads = new LinkedHashSet<>();
			List<StructureItem> items = new ArrayList<>();
			for (int j = 0; j < installment.items().size(); j++) {
				FeeStructureRequest.Item item = installment.items().get(j);
				if (!seenHeads.add(item.headId())) {
					violations.add(new FieldViolation(field + ".items[" + j + "].headId",
							"is charged twice in this installment"));
					continue;
				}
				Map<String, Long> overrides = validatedSectionAmounts(item.sectionAmounts(), schoolClass,
						field + ".items[" + j + "].sectionAmounts", violations);
				if (headsById.containsKey(item.headId())) {
					items.add(new StructureItem(item.headId(), item.amount(), overrides));
				}
			}
			result.add(new Installment(name, installment.dueDate(), items,
					installment.kind() == null ? InstallmentKind.MONTHLY : installment.kind(),
					trimToNull(installment.label())));
		}

		if (!violations.isEmpty()) {
			throw new ValidationException("The fee structure could not be saved", violations);
		}
		return result;
	}

	/**
	 * Section overrides, with the keys upper-cased to match how sections are stored, and every key
	 * checked against the class.
	 *
	 * <p>A key for a section the class does not have is refused rather than ignored: it is nearly
	 * always a typo, and the cost of ignoring it is that those students are billed the default amount
	 * and nobody finds out until a parent asks.
	 *
	 * @return null when there are no overrides, so the stored document stays as it was before this
	 *         field existed
	 */
	private static Map<String, Long> validatedSectionAmounts(Map<String, Long> requested, SchoolClass schoolClass,
			String field, List<FieldViolation> violations) {
		if (requested == null || requested.isEmpty()) {
			return null;
		}
		List<String> sections = schoolClass.getSections() == null ? List.of() : schoolClass.getSections();
		Map<String, Long> normalized = new LinkedHashMap<>();
		requested.forEach((section, amount) -> {
			String key = section.trim().toUpperCase(Locale.ROOT);
			if (!sections.contains(key)) {
				violations.add(new FieldViolation(field,
						schoolClass.getName() + " has no section " + key));
				return;
			}
			normalized.put(key, amount);
		});
		return normalized.isEmpty() ? null : normalized;
	}

	/** Every head the request mentions, keyed by id. Missing or retired ones become violations. */
	private Map<String, FeeHead> activeHeadsFor(FeeStructureRequest request, List<FieldViolation> violations) {
		Set<String> ids = request.installments().stream()
				.flatMap(installment -> installment.items().stream())
				.map(FeeStructureRequest.Item::headId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		Map<String, FeeHead> found = headService.findAllByIdIn(ids).stream()
				.collect(Collectors.toMap(FeeHead::getId, head -> head));
		for (String id : ids) {
			FeeHead head = found.get(id);
			if (head == null) {
				violations.add(new FieldViolation("installments[].items[].headId", "no fee head with id " + id));
			}
			else if (!head.isActive()) {
				// A retired head may stay on invoices already issued, but putting it on a new structure
				// is almost always a stale form rather than an intention.
				violations.add(new FieldViolation("installments[].items[].headId",
						head.getName() + " is no longer active"));
				found.remove(id);
			}
		}
		return found;
	}

	private void requireInstallmentsWithInvoicesKept(String structureId, List<Installment> installments) {
		Set<String> kept = installments.stream().map(Installment::name).collect(Collectors.toSet());
		Set<String> billed = invoices.findByStructureId(structureId).stream()
				.map(Invoice::getInstallmentName)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		List<String> lost = billed.stream().filter(name -> !kept.contains(name)).toList();
		if (!lost.isEmpty()) {
			throw new BusinessRuleException("These installments already have invoices and cannot be renamed or "
					+ "removed: " + String.join(", ", lost) + ". The installment name is part of what makes "
					+ "invoice generation idempotent, so renaming one would bill those students again.");
		}
	}

	/** Blank becomes null, so an empty form field is stored as "absent" rather than as "". */
	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static LateFineRule toRule(FeeStructureRequest.LateFine lateFine) {
		return lateFine == null ? null
				: new LateFineRule(lateFine.type(), lateFine.amount(), lateFine.graceDays(), lateFine.cap());
	}

	private static Set<String> headIdsOf(List<FeeStructure> forStructures) {
		return forStructures.stream()
				.filter(structure -> structure.getInstallments() != null)
				.flatMap(structure -> structure.getInstallments().stream())
				.filter(installment -> installment.items() != null)
				.flatMap(installment -> installment.items().stream())
				.map(StructureItem::headId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private FeeStructure insert(FeeStructure structure) {
		try {
			return structures.insert(structure);
		}
		catch (DuplicateKeyException ex) {
			throw new ConflictException("That class already has a fee structure for this session", ex);
		}
	}
}

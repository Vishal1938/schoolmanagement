package com.school.fees.app;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.school.academics.app.AcademicContext;
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
import com.school.fees.domain.FeeHead;
import com.school.fees.domain.FeeStructure;
import com.school.fees.domain.Installment;
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

	private final FeeStructureRepository structures;
	private final InvoiceRepository invoices;
	private final FeeHeadService headService;
	private final SchoolClassService classes;
	private final AcademicContext academicContext;
	private final AuditService audit;
	private final Clock clock;

	public FeeStructureService(FeeStructureRepository structures, InvoiceRepository invoices,
			FeeHeadService headService, SchoolClassService classes, AcademicContext academicContext,
			AuditService audit, Clock clock) {
		this.structures = structures;
		this.invoices = invoices;
		this.headService = headService;
		this.classes = classes;
		this.academicContext = academicContext;
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
		List<Installment> installments = validatedInstallments(request);
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
		List<Installment> installments = validatedInstallments(request);
		requireInstallmentsWithInvoicesKept(id, installments);

		FeeStructure saved = structures.save(before.toBuilder()
				.installments(installments)
				.lateFine(toRule(request.lateFine()))
				.updatedAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.FEE_STRUCTURE_UPDATED, AUDIT_ENTITY, id, before, saved);
		return saved;
	}

	// --- validation -------------------------------------------------------------------------------

	private SchoolClass validatedClass(String classId) {
		return classes.findById(classId.trim()).orElseThrow(() ->
				new ValidationException("The fee structure does not point at a real class",
						List.of(new FieldViolation("classId", "no class with id " + classId))));
	}

	/**
	 * Installment names unique within the structure, every head id real and still active, and no head
	 * charged twice in one installment.
	 */
	private List<Installment> validatedInstallments(FeeStructureRequest request) {
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
			Set<String> seenHeads = new LinkedHashSet<>();
			List<StructureItem> items = new ArrayList<>();
			for (int j = 0; j < installment.items().size(); j++) {
				FeeStructureRequest.Item item = installment.items().get(j);
				if (!seenHeads.add(item.headId())) {
					violations.add(new FieldViolation(field + ".items[" + j + "].headId",
							"is charged twice in this installment"));
					continue;
				}
				if (headsById.containsKey(item.headId())) {
					items.add(new StructureItem(item.headId(), item.amount()));
				}
			}
			result.add(new Installment(name, installment.dueDate(), items));
		}

		if (!violations.isEmpty()) {
			throw new ValidationException("The fee structure could not be saved", violations);
		}
		return result;
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

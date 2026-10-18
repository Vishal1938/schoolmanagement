package com.school.fees.app;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.school.academics.app.AcademicContext;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.BusinessRuleException;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ValidationException;
import com.school.fees.api.StudentChargeRequest;
import com.school.fees.api.StudentChargeResponse;
import com.school.fees.domain.FeeHead;
import com.school.fees.domain.InstallmentKind;
import com.school.fees.domain.Invoice;
import com.school.fees.domain.InvoiceItem;
import com.school.fees.domain.InvoiceStatus;
import com.school.fees.infra.InvoiceRepository;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Charges raised against named students rather than against a class — a fine, a trip, a lost book.
 *
 * <p>These are ordinary invoices of kind {@code CUSTOM}, and that is the whole design: they appear on
 * the student's ledger, they are paid through the same counter and gateway flows, they land in the
 * same collection report under their own head, and they come out on the same receipt as a month's
 * fees. The only thing that makes them different is a null {@code structureId} — they belong to no
 * structure, so no late-fine rule reaches them and {@code generate-invoices} will never touch them.
 *
 * <p>Separate from {@link InvoiceService} because the dependency sets differ: this needs students and
 * fee heads and nothing else, while that one carries concessions, structures and the whole payment
 * allocation. Both write {@code fee_invoices}, which is one module's own collection.
 */
@Service
public class StudentChargeService {

	/** Same entity type as every other invoice, so one audit search finds the whole history of a bill. */
	private static final String AUDIT_ENTITY = InvoiceService.AUDIT_ENTITY;

	/**
	 * How many times a charge of the same name is suffixed before giving up, e.g. {@code "Late fine
	 * (2)"}. A student who has had the same fine twenty times in one year is a conversation, not a
	 * data-entry problem.
	 */
	private static final int MAX_NAME_ATTEMPTS = 20;

	private final InvoiceRepository invoices;
	private final FeeHeadService heads;
	private final StudentService students;
	private final AcademicContext academicContext;
	private final AuditService audit;
	private final Clock clock;

	public StudentChargeService(InvoiceRepository invoices, FeeHeadService heads, StudentService students,
			AcademicContext academicContext, AuditService audit, Clock clock) {
		this.invoices = invoices;
		this.heads = heads;
		this.students = students;
		this.academicContext = academicContext;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Raises one invoice per student named.
	 *
	 * <p><strong>Validated end to end before anything is written</strong>, the same stance as the
	 * spreadsheet import: an unknown student id or a retired head rejects the whole request with every
	 * problem listed, rather than charging eleven of the twelve students somebody pasted in. The writes
	 * then run in one transaction, so even a surprise at that stage leaves the collection as it was.
	 *
	 * <p>No concession is applied. A concession is a reduction of the <em>fees</em> a family was
	 * assessed for the year; a fine is not that, and quietly waiving half of one because the student
	 * has a sibling discount would be wrong.
	 */
	@Transactional
	public StudentChargeResponse raise(StudentChargeRequest request) {
		FeeHead head = validatedActiveHead(request.headId());
		List<StudentRef> roster = validatedStudents(request.studentUniqueIds());
		// Stamped with the session even though no structure is involved, because the defaulters list and
		// the collection report both narrow by it: a fine nobody has paid has to show up on the chase
		// list beside the fees, not vanish because it was raised outside a structure.
		String sessionId = academicContext.currentSessionId();
		String label = request.name().trim();
		String note = trimToNull(request.note());
		Instant now = Instant.now(clock);

		List<StudentChargeResponse.Charge> charges = new ArrayList<>(roster.size());
		for (StudentRef student : roster) {
			InvoiceItem item = new InvoiceItem(head.getId(), head.getName(), request.amount());
			Invoice saved = insert(student, sessionId, label, item, request, note, now);
			audit.record(AuditAction.FEE_CHARGE_CREATED, AUDIT_ENTITY, saved.getId(), null, saved);
			charges.add(new StudentChargeResponse.Charge(saved.getId(), student.uniqueId(), student.name(),
					saved.getInstallmentName(), saved.displayLabel(), saved.getNetAmount()));
		}
		return new StudentChargeResponse(charges.size(), charges);
	}

	/**
	 * Withdraws a charge, but only while nothing has been paid against it.
	 *
	 * <p>Deleted rather than cancelled, which is the opposite of the rule for every other invoice. The
	 * reason {@code CANCELLED} exists is that receipts and reports point at invoices; a charge nobody
	 * has paid a rupee towards is pointed at by nothing, and a fine entered against the wrong student
	 * should leave their ledger entirely rather than sit on it forever explaining itself. Once money
	 * has been taken the usual rule returns and this is refused.
	 */
	@Transactional
	public void delete(String invoiceId) {
		Invoice invoice = invoices.findById(invoiceId)
				.orElseThrow(() -> NotFoundException.of("Invoice", invoiceId));
		if (invoice.resolvedKind() != InstallmentKind.CUSTOM) {
			throw new BusinessRuleException("That is a " + invoice.resolvedKind() + " invoice raised from a fee "
					+ "structure, not an individual charge. Only individual charges can be deleted.");
		}
		if (!invoice.untouched()) {
			throw new BusinessRuleException("This charge has already been paid against, so it cannot be deleted "
					+ "— a receipt has been issued naming it. Raise a concession or a refund instead.");
		}
		invoices.delete(invoice);
		audit.record(AuditAction.FEE_CHARGE_DELETED, AUDIT_ENTITY, invoiceId, invoice, null);
	}

	// --- internals --------------------------------------------------------------------------------

	/**
	 * Writes the invoice, suffixing the installment name if that student already has a charge under it.
	 *
	 * <p>The unique index on {@code (studentUniqueId, structureId, installmentName)} is what forces
	 * this: with a null {@code structureId} it means one student cannot hold two charges of the same
	 * name. A second "Late library fine" in March is a perfectly normal thing to happen, so the stored
	 * name becomes {@code "Late library fine (2)"} while the {@code label} stays what was typed — the
	 * family sees the same words on both bills, and the index still does its job.
	 */
	private Invoice insert(StudentRef student, String sessionId, String label, InvoiceItem item,
			StudentChargeRequest request, String note, Instant now) {
		for (int attempt = 1; attempt <= MAX_NAME_ATTEMPTS; attempt++) {
			String name = attempt == 1 ? label : label + " (" + attempt + ")";
			try {
				return invoices.insert(Invoice.builder()
						.studentUniqueId(student.uniqueId())
						.classId(student.classId())
						.sessionId(sessionId)
						.structureId(null)
						.installmentName(name)
						.kind(InstallmentKind.CUSTOM)
						.label(label)
						.note(note)
						.dueDate(request.dueDate())
						.items(List.of(item))
						.concessionAmount(0L)
						.netAmount(item.amount())
						.paidAmount(0L)
						.lateFinePaid(0L)
						// A zero-amount charge is settled the moment it exists, the same rule generation
						// uses for a full waiver: an UNPAID invoice for nothing is a row every report has
						// to explain away.
						.status(item.amount() <= 0L ? InvoiceStatus.PAID : InvoiceStatus.UNPAID)
						.createdAt(now)
						.updatedAt(now)
						.build());
			}
			catch (DuplicateKeyException ex) {
				if (attempt == MAX_NAME_ATTEMPTS) {
					throw new BusinessRuleException(student.name() + " already has " + MAX_NAME_ATTEMPTS
							+ " charges called \"" + label + "\". Give this one a more specific name.");
				}
			}
		}
		throw new IllegalStateException("unreachable");
	}

	/** Every id resolved to a student, with all the unknown ones reported at once. */
	private List<StudentRef> validatedStudents(List<String> uniqueIds) {
		List<FieldViolation> violations = new ArrayList<>();
		List<StudentRef> found = new ArrayList<>(uniqueIds.size());
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < uniqueIds.size(); i++) {
			String uniqueId = uniqueIds.get(i);
			String field = "studentUniqueIds[" + i + "]";
			StudentRef student = students.findRef(uniqueId).orElse(null);
			if (student == null) {
				violations.add(new FieldViolation(field, "no student with id " + uniqueId));
			}
			else if (!seen.add(student.uniqueId())) {
				// Refused rather than silently deduplicated: the clerk either pasted a list twice or
				// meant to charge twice, and guessing which is worse than asking.
				violations.add(new FieldViolation(field, student.uniqueId() + " is listed twice"));
			}
			else {
				found.add(student);
			}
		}
		if (!violations.isEmpty()) {
			throw new ValidationException("Those students could not be charged", violations);
		}
		return found;
	}

	private FeeHead validatedActiveHead(String headId) {
		FeeHead head = heads.findAllByIdIn(Set.of(headId.trim())).stream().findFirst()
				.orElseThrow(() -> new ValidationException("That fee head does not exist",
						List.of(new FieldViolation("headId", "no fee head with id " + headId))));
		if (!head.isActive()) {
			throw new ValidationException("That fee head has been retired",
					List.of(new FieldViolation("headId", head.getName() + " is no longer active")));
		}
		return head;
	}

	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

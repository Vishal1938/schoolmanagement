package com.school.fees.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.school.academics.app.AcademicContext;
import com.school.academics.app.AcademicSessionService;
import com.school.academics.app.SchoolClassService;
import com.school.academics.domain.AcademicSession;
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
import com.school.fees.api.SemesterFeeSetupRequest;
import com.school.fees.api.SemesterSuggestionResponse;
import com.school.fees.domain.FeeHead;
import com.school.fees.domain.FeePeriod;
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

	// --- semester plans ---------------------------------------------------------------------------

	/**
	 * Writes {@code SEMESTER} installments into the structure for one session and course, creating the
	 * structure if there is none and <strong>extending</strong> it if there is.
	 *
	 * <p>The three outcomes per semester are exactly {@link #setUpMonthly}'s, and for the same reasons:
	 * a semester the structure does not have is added, one it has with no invoices takes this head's new
	 * amount with every other head left alone, and one that already has invoices is skipped entirely
	 * because an invoice states what a family was charged.
	 *
	 * <p>Two things are refused outright rather than reconciled. A semester <strong>number listed
	 * twice</strong> in one request, because the number is what the installment is named after and the
	 * second would silently overwrite the first. And <strong>overlapping periods</strong>, checked both
	 * within the request and against the semesters already on the structure: a day of the session
	 * belongs to one semester, and two semesters covering today would make "the installment covering
	 * today" an ambiguous question on every dashboard and register that asks it.
	 *
	 * <p>Nothing is billed here. The invoices follow from the ordinary
	 * {@code POST /fees/structures/{id}/generate-invoices}.
	 */
	public SemesterPlanResult setUpSemesters(SemesterFeeSetupRequest request) {
		String sessionId = resolvedSessionId(request.sessionId());
		SchoolClass schoolClass = validatedClass(request.classId());
		FeeHead head = validatedActiveHead(request.headId());
		List<FieldViolation> violations = new ArrayList<>();
		Map<String, Long> overrides = validatedSectionAmounts(request.sectionAmounts(), schoolClass,
				"sectionAmounts", violations);

		FeeStructure before = structures.findBySessionIdAndClassId(sessionId, schoolClass.getId()).orElse(null);
		List<SemesterFeeSetupRequest.Semester> requested = validatedSemesters(request, before, violations);
		if (!violations.isEmpty()) {
			throw new ValidationException("The semester fee plan could not be saved", violations);
		}

		Set<String> billed = before == null ? Set.of() : installmentNamesWithInvoices(before.getId());
		Map<String, Installment> byName = new LinkedHashMap<>();
		if (before != null) {
			before.installmentsOrEmpty().forEach(installment -> byName.put(installment.name(), installment));
		}

		int created = 0;
		int updated = 0;
		int skipped = 0;
		for (SemesterFeeSetupRequest.Semester semester : requested) {
			String name = semesterName(semester.number());
			String label = hasText(semester.label()) ? semester.label().trim() : semesterLabel(semester);
			Installment existing = byName.get(name);
			if (existing == null) {
				byName.put(name, new Installment(name, semester.dueDate(),
						List.of(new StructureItem(head.getId(), request.amount(), overrides)),
						InstallmentKind.SEMESTER, label, semester.periodStart(), semester.periodEnd()));
				created++;
			}
			else if (billed.contains(name)) {
				skipped++;
			}
			else {
				byName.put(name, withSemesterAmount(existing, head.getId(), request.amount(), overrides, label,
						semester));
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
		return new SemesterPlanResult(saved, created, updated, skipped);
	}

	/** The structure after a semester plan was written, with what happened to each semester. */
	public record SemesterPlanResult(FeeStructure structure, int created, int updated, int skipped) {
	}

	/**
	 * Two semesters filled in for one course, for an office to review rather than type.
	 *
	 * <p>Reads nothing but the course name and the session's dates, and writes nothing at all. The
	 * numbers come from a {@code "Year N"} in the course name — the second year of a programme is its
	 * third and fourth semesters — falling back to 1 and 2 when the name says nothing. The periods are
	 * the usual Indian split, July to December and January to June, with the fees falling due at the end
	 * of each opening month.
	 */
	public SemesterSuggestionResponse suggestSemesters(String classId, String sessionId) {
		SchoolClass schoolClass = validatedClass(classId);
		AcademicSession session = resolvedSession(sessionId);
		Integer courseYear = courseYearIn(schoolClass.getName());
		int first = courseYear == null ? 1 : 2 * courseYear - 1;
		// The year the session opens in, so a 2026-27 session suggests Jul-Dec 2026 and Jan-Jun 2027.
		int openingYear = session.getStartDate() == null
				? LocalDate.now(clock).getYear()
				: session.getStartDate().getYear();

		List<SemesterSuggestionResponse.Semester> semesters = List.of(
				suggestion(first, LocalDate.of(openingYear, 7, 1), LocalDate.of(openingYear, 12, 31),
						LocalDate.of(openingYear, 7, 31)),
				suggestion(first + 1, LocalDate.of(openingYear + 1, 1, 1), LocalDate.of(openingYear + 1, 6, 30),
						LocalDate.of(openingYear + 1, 1, 31)));
		return new SemesterSuggestionResponse(session.getId(), schoolClass.getId(), schoolClass.getName(),
				courseYear, semesters);
	}

	// --- semester internals -----------------------------------------------------------------------

	/** {@code "Year 2"}, {@code "2nd Year"} or {@code "II Year"} is beyond us; only the first two count. */
	private static final Pattern COURSE_YEAR = Pattern.compile(
			"(?:year\\s*(\\d{1,2}))|(?:(\\d{1,2})\\s*(?:st|nd|rd|th)?\\s*year)", Pattern.CASE_INSENSITIVE);

	/** How a semester installment is named: {@code "Semester 3"}. An identifier, so no period in it. */
	private static String semesterName(int number) {
		return "Semester " + number;
	}

	/** How it is displayed: {@code "Semester 3 (Jul-Dec 2026)"}. */
	private static String semesterLabel(SemesterFeeSetupRequest.Semester semester) {
		return semesterName(semester.number()) + " ("
				+ new FeePeriod(semester.periodStart(), semester.periodEnd()).describe() + ")";
	}

	private static SemesterSuggestionResponse.Semester suggestion(int number, LocalDate start, LocalDate end,
			LocalDate dueDate) {
		return new SemesterSuggestionResponse.Semester(number,
				semesterName(number) + " (" + new FeePeriod(start, end).describe() + ")", start, end, dueDate);
	}

	/** The year a course name claims to be, or null when it does not say. */
	private static Integer courseYearIn(String className) {
		if (className == null) {
			return null;
		}
		Matcher matcher = COURSE_YEAR.matcher(className);
		if (!matcher.find()) {
			return null;
		}
		String digits = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
		int year = Integer.parseInt(digits);
		// A "Year 0" or a "Year 40" is somebody's room number, not a course year.
		return year >= 1 && year <= 8 ? year : null;
	}

	/**
	 * The requested semesters, with duplicate numbers and overlapping periods reported.
	 *
	 * <p>Overlaps are checked against the semesters already on the structure as well as within the
	 * request, but a stored semester whose <em>number</em> the request also carries is left out of that
	 * comparison: the request is replacing it, so it cannot overlap itself.
	 */
	private static List<SemesterFeeSetupRequest.Semester> validatedSemesters(SemesterFeeSetupRequest request,
			FeeStructure before, List<FieldViolation> violations) {
		List<SemesterFeeSetupRequest.Semester> requested = request.semesters();
		Set<Integer> seenNumbers = new LinkedHashSet<>();
		// A list rather than a map keyed by period: two semesters covering the *same* dates is the
		// clearest overlap there is, and a map would quietly collapse them into one entry.
		List<NamedPeriod> periods = new ArrayList<>();
		List<SemesterFeeSetupRequest.Semester> result = new ArrayList<>();

		for (int i = 0; i < requested.size(); i++) {
			SemesterFeeSetupRequest.Semester semester = requested.get(i);
			String field = "semesters[" + i + "]";
			if (!seenNumbers.add(semester.number())) {
				violations.add(new FieldViolation(field + ".number", "semester " + semester.number()
						+ " is listed twice; one semester number is one installment"));
				continue;
			}
			if (semester.periodEnd().isBefore(semester.periodStart())) {
				violations.add(new FieldViolation(field + ".periodEnd", "must not be before periodStart"));
				continue;
			}
			periods.add(new NamedPeriod(semesterName(semester.number()),
					new FeePeriod(semester.periodStart(), semester.periodEnd())));
			result.add(semester);
		}

		// The semesters already stored that this request is not replacing.
		if (before != null) {
			Set<String> replaced = result.stream().map(semester -> semesterName(semester.number()))
					.collect(Collectors.toSet());
			before.installmentsOrEmpty().stream()
					.filter(installment -> installment.resolvedKind() == InstallmentKind.SEMESTER)
					.filter(installment -> !replaced.contains(installment.name()))
					.filter(installment -> installment.period() != null)
					.forEach(installment -> periods.add(new NamedPeriod(installment.name(), installment.period())));
		}

		for (int i = 0; i < periods.size(); i++) {
			for (int j = i + 1; j < periods.size(); j++) {
				if (periods.get(i).period().overlaps(periods.get(j).period())) {
					violations.add(new FieldViolation("semesters[].periodStart",
							periods.get(i).name() + " and " + periods.get(j).name() + " cover overlapping "
									+ "periods; a day of the session belongs to one semester"));
				}
			}
		}
		return result;
	}

	/** One period with the installment name to quote for it in a violation message. */
	private record NamedPeriod(String name, FeePeriod period) {
	}

	/** The semester with this head's amount set, every other head kept, and the dates refreshed. */
	private static Installment withSemesterAmount(Installment installment, String headId, long amount,
			Map<String, Long> overrides, String label, SemesterFeeSetupRequest.Semester semester) {
		return new Installment(installment.name(), semester.dueDate(),
				itemsWithHeadAmount(installment, headId, amount, overrides),
				InstallmentKind.SEMESTER, label, semester.periodStart(), semester.periodEnd());
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

	// --- deleting ---------------------------------------------------------------------------------

	/**
	 * Removes a whole fee structure and the invoices raised from it.
	 *
	 * <p><strong>Only while nothing has been paid.</strong> One rupee taken against any one of its
	 * invoices refuses the whole deletion, because a receipt has then been issued naming an installment
	 * of this structure and a receipt has to keep pointing at something. The way to stop a structure
	 * counting after that is to cancel its invoices, not to delete it.
	 *
	 * <p>Invoices are otherwise never deleted in this module — {@code CANCELLED} is how one stops
	 * counting — and this is the same deliberate exception {@link #deleteOneTimeFee} is: a course set up
	 * against the wrong class and billed the same afternoon has nothing worth keeping, and leaving two
	 * hundred cancelled invoices behind would make every register harder to read for no gain.
	 */
	public void delete(String structureId) {
		FeeStructure before = get(structureId);
		List<Invoice> raised = invoices.findByStructureId(structureId);
		requireNothingPaid(raised, "This fee structure");

		invoices.deleteAll(raised);
		structures.delete(before);
		audit.record(AuditAction.FEE_STRUCTURE_DELETED, AUDIT_ENTITY, structureId, before,
				Map.of("classId", before.getClassId(), "sessionId", before.getSessionId(),
						"installments", before.installmentsOrEmpty().size(), "invoicesDeleted", raised.size()));
	}

	/**
	 * Removes one installment — a month, a semester or a one-off fee — and the invoices raised from it.
	 *
	 * <p>The same rule as {@link #delete}, applied to one column instead of the sheet: refused once
	 * anything has been taken against any of that installment's invoices. This is the general form of
	 * {@link #deleteOneTimeFee}, which stays as it is for a client that already calls it; the difference
	 * is only that this one does not insist the installment be a one-off fee, because a semester written
	 * with the wrong dates has to be removable too.
	 *
	 * @return the structure as it now stands
	 */
	public FeeStructure deleteInstallment(String structureId, String installmentName) {
		FeeStructure before = get(structureId);
		Installment existing = before.installmentNamed(installmentName)
				.orElseThrow(() -> new NotFoundException("This structure has no installment called \""
						+ installmentName + "\""));
		List<Invoice> raised = invoicesFor(before.getId(), existing.name());
		requireNothingPaid(raised, "\"" + existing.name() + "\"");

		List<Installment> installments = before.installmentsOrEmpty().stream()
				.filter(installment -> !installment.name().equals(existing.name()))
				.collect(Collectors.toCollection(ArrayList::new));
		FeeStructure saved = saveInstallments(before, installments);
		invoices.deleteAll(raised);
		audit.record(AuditAction.FEE_INSTALLMENT_DELETED, AUDIT_ENTITY, structureId, existing,
				Map.of("installmentName", existing.name(), "kind", existing.resolvedKind(),
						"invoicesDeleted", raised.size()));
		return saved;
	}

	/** Refuses the deletion if money has been taken against any of these invoices, naming how many. */
	private static void requireNothingPaid(List<Invoice> raised, String what) {
		List<Invoice> paid = raised.stream().filter(invoice -> !invoice.untouched()).toList();
		if (!paid.isEmpty()) {
			throw new BusinessRuleException(what + " cannot be deleted: " + paid.size() + " of its "
					+ raised.size() + " invoices have had money taken against them, and a receipt has been "
					+ "issued naming them. Cancel the invoices instead, or raise a concession.");
		}
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

	/** The same, as the whole session, for a caller that needs its dates as well as its id. */
	private AcademicSession resolvedSession(String sessionId) {
		return hasText(sessionId) ? sessions.get(sessionId.trim()) : academicContext.currentSession();
	}

	/** The named day of that month, or its last day when the month is shorter. */
	private static LocalDate dueDateIn(YearMonth month, int dueDay) {
		return month.atDay(Math.min(dueDay, month.lengthOfMonth()));
	}

	/** The installment with this head's amount set, every other head kept, and the due date refreshed. */
	private static Installment withHeadAmount(Installment installment, String headId, long amount,
			Map<String, Long> overrides, LocalDate dueDate) {
		return new Installment(installment.name(), dueDate,
				itemsWithHeadAmount(installment, headId, amount, overrides),
				InstallmentKind.MONTHLY, installment.label());
	}

	/**
	 * This installment's items with one head's amount rewritten, or added if it was not charged yet.
	 *
	 * <p>Every other head is kept exactly as it was: the monthly and semester setup endpoints are each
	 * about one head, and must not quietly drop the transport fee somebody added by hand.
	 */
	private static List<StructureItem> itemsWithHeadAmount(Installment installment, String headId, long amount,
			Map<String, Long> overrides) {
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
		return items;
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
					+ " installment, not a one-off fee. Recurring installments are managed through "
					+ "POST /fees/structures/monthly, POST /fees/structures/semester or "
					+ "PUT /fees/structures/{id}, and any of them can be removed with "
					+ "DELETE /fees/structures/{id}/installments/{name}.");
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
						"must be MONTHLY, SEMESTER or ONE_TIME; a charge for named students is "
								+ "POST /fees/charges"));
				continue;
			}
			InstallmentKind kind = installment.kind() == null ? InstallmentKind.MONTHLY : installment.kind();
			FeePeriod period = validatedPeriod(installment, kind, field, violations);
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
			result.add(new Installment(name, installment.dueDate(), items, kind, trimToNull(installment.label()),
					period == null ? null : period.start(), period == null ? null : period.end()));
		}

		requireNoOverlappingSemesters(result, violations);
		if (!violations.isEmpty()) {
			throw new ValidationException("The fee structure could not be saved", violations);
		}
		return result;
	}

	/**
	 * The period on one requested installment, checked against its kind.
	 *
	 * <p>A SEMESTER must state both dates: its period is the one thing that cannot be derived, and a
	 * semester without one would be invisible to every "which installment covers today" question. A
	 * MONTHLY one may state them and otherwise falls back to the month of its due date. A ONE_TIME fee
	 * covers no period at all, so dates on one are refused rather than stored and ignored.
	 *
	 * @return null when the installment carries no explicit period
	 */
	private static FeePeriod validatedPeriod(FeeStructureRequest.Installment requested, InstallmentKind kind,
			String field, List<FieldViolation> violations) {
		LocalDate start = requested.periodStart();
		LocalDate end = requested.periodEnd();
		if (start == null && end == null) {
			if (kind == InstallmentKind.SEMESTER) {
				violations.add(new FieldViolation(field + ".periodStart",
						"a SEMESTER installment must state periodStart and periodEnd; unlike a month, its "
								+ "period cannot be worked out from the due date"));
			}
			return null;
		}
		if (start == null || end == null) {
			violations.add(new FieldViolation(field + (start == null ? ".periodStart" : ".periodEnd"),
					"periodStart and periodEnd have to be given together"));
			return null;
		}
		if (kind == InstallmentKind.ONE_TIME) {
			violations.add(new FieldViolation(field + ".periodStart",
					"a ONE_TIME fee covers no period, so it must not carry one"));
			return null;
		}
		if (end.isBefore(start)) {
			violations.add(new FieldViolation(field + ".periodEnd", "must not be before periodStart"));
			return null;
		}
		return new FeePeriod(start, end);
	}

	/**
	 * No two semesters of one structure may share a day.
	 *
	 * <p>Only the semesters are checked. Months cannot overlap by construction, and a structure that
	 * mixes the two — a college moving to semesters mid-session — necessarily has a semester sitting on
	 * top of its months, which is a real state while the changeover happens rather than a mistake.
	 */
	private static void requireNoOverlappingSemesters(List<Installment> installments,
			List<FieldViolation> violations) {
		List<Installment> semesters = installments.stream()
				.filter(installment -> installment.resolvedKind() == InstallmentKind.SEMESTER)
				.filter(installment -> installment.period() != null)
				.sorted(Comparator.comparing(Installment::resolvedPeriodStart))
				.toList();
		for (int i = 1; i < semesters.size(); i++) {
			Installment earlier = semesters.get(i - 1);
			Installment later = semesters.get(i);
			if (earlier.period().overlaps(later.period())) {
				violations.add(new FieldViolation("installments[].periodStart",
						"\"" + earlier.name() + "\" and \"" + later.name() + "\" cover overlapping periods; "
								+ "a day of the session belongs to one semester"));
			}
		}
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

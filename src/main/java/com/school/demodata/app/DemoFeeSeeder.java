package com.school.demodata.app;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.school.academics.domain.SchoolClass;
import com.school.common.exceptions.AppException;
import com.school.fees.app.FeeHeadService;
import com.school.fees.app.FeeStructureService;
import com.school.fees.app.InvoiceCounts;
import com.school.fees.app.InvoiceService;
import com.school.fees.app.StudentChargeService;
import com.school.fees.domain.FeePeriod;
import com.school.fees.domain.Installment;
import com.school.fees.domain.InstallmentKind;
import com.school.fees.domain.Invoice;
import com.school.fees.domain.LateFineRule;
import com.school.fees.domain.LateFineType;
import com.school.fees.domain.StructureItem;
import com.school.payment.app.PaymentService;
import com.school.payment.domain.PayerRelation;
import com.school.payment.domain.Payment;
import com.school.payment.domain.PaymentMode;
import com.school.people.app.GuardianNames;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The money side of the demonstration data set: fee heads, semester structures, invoices, individual
 * charges and a term of backdated payments.
 *
 * <p>Nothing here writes a document. Every step goes through the fees and payment modules' public
 * services, so the invoices carry the same snapshots, the fines are computed by the same rule and the
 * receipts are numbered off the same counter as a real counter payment — which is what makes the
 * resulting registers and reports add up.
 *
 * <p>Two things decide the shape of this class. <strong>Payments are planned and then executed in
 * date order</strong>, for the receipt-numbering reason {@link PlannedPayment} explains. And
 * <strong>a step that cannot find what the specification named warns rather than fails</strong>: the
 * names of three individual students are in there, and a demonstration college that spells one of
 * them differently should still get its fees, not a 404.
 */
@Component
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled", havingValue = "true")
class DemoFeeSeeder {

	private static final Logger log = LoggerFactory.getLogger(DemoFeeSeeder.class);

	// --- the fee heads this data set charges under ------------------------------------------------

	private static final String SEMESTER_FEE = "Semester fee";
	private static final String ADMISSION_FEE = "Admission fee";
	private static final String EXAM_FEE = "Exam fee";
	private static final String LIBRARY_FINE = "Library fine";
	private static final String CERTIFICATE_FEE = "Certificate fee";

	private static final List<String> HEADS = List.of(SEMESTER_FEE, ADMISSION_FEE, EXAM_FEE, LIBRARY_FINE,
			CERTIFICATE_FEE);

	/** ₹10 a day after a fortnight's grace, never more than ₹500 on one invoice. */
	private static final LateFineRule LATE_FINE =
			new LateFineRule(LateFineType.PER_DAY, 10_00L, 15, 500_00L);

	/** How many payment patterns there are; the roll number picks one. */
	private static final int PATTERNS = 5;

	private final FeeHeadService heads;
	private final FeeStructureService structures;
	private final InvoiceService invoices;
	private final StudentChargeService charges;
	private final PaymentService payments;
	private final StudentService students;

	DemoFeeSeeder(FeeHeadService heads, FeeStructureService structures, InvoiceService invoices,
			StudentChargeService charges, PaymentService payments, StudentService students) {
		this.heads = heads;
		this.structures = structures;
		this.invoices = invoices;
		this.charges = charges;
		this.payments = payments;
		this.students = students;
	}

	/**
	 * Writes the whole fee and payment story for one session.
	 *
	 * @param courses the courses named in {@link DemoCourse#ALL}, already resolved and checked to exist
	 * @param today   from the injected clock, so "today's collection" is non-zero on the day this runs
	 */
	void seed(String sessionId, Map<String, SchoolClass> courses, LocalDate today, DemoSummary summary) {
		Map<String, String> headIds = seedHeads(summary);
		Map<String, String> structureIds = seedStructures(courses, headIds, summary);
		generateInvoices(structureIds, summary);

		List<PlannedPayment> plan = new ArrayList<>();
		plan.addAll(planCoursePayments(sessionId, courses, today, summary));
		plan.addAll(seedIndividualCharges(sessionId, courses, headIds, summary));
		execute(plan, summary);

		// Last, and deliberately: his invoices had to be raised and his payments applied while he was
		// still ACTIVE, because generate-invoices and the roll both skip a student who has left.
		withdrawStudent(sessionId, courses, summary);
	}

	// --- 2. fee heads -----------------------------------------------------------------------------

	/** @return head name to id, creating any head this deployment has not got */
	private Map<String, String> seedHeads(DemoSummary summary) {
		Map<String, String> byName = new LinkedHashMap<>();
		for (String name : HEADS) {
			byName.put(name, heads.findOrCreate(name).getId());
			summary.addFeeHead();
		}
		log.info("Demonstration fee heads ready: {}", HEADS);
		return byName;
	}

	// --- 3. fee structures and invoices -----------------------------------------------------------

	/**
	 * One structure per course, written whole: both semesters, the exam fees, the admission fee where
	 * there is one, and the late-fine rule, all in a single call.
	 *
	 * <p>Whole rather than built up through {@code /fees/structures/semester} and
	 * {@code /one-time-fees}, because neither of those sets a late-fine rule — and without a rule
	 * there is no fine, which is half of what this data set exists to show.
	 *
	 * @return course name to structure id
	 */
	private Map<String, String> seedStructures(Map<String, SchoolClass> courses, Map<String, String> headIds,
			DemoSummary summary) {
		Map<String, String> structureIds = new LinkedHashMap<>();
		for (DemoCourse course : DemoCourse.ALL) {
			SchoolClass schoolClass = courses.get(course.courseName());
			Optional<String> existing = structures.list(null, schoolClass.getId()).stream()
					.map(found -> found.getId())
					.findFirst();
			if (existing.isPresent()) {
				// A re-seed after a wipe that was interrupted. The installments are the same, so there
				// is nothing to correct; the invoices below are idempotent and will fill in the gaps.
				structureIds.put(course.courseName(), existing.get());
				summary.warn(course.courseName() + " already had a fee structure for this session; it was "
						+ "left as it is and invoices were generated from it.");
				continue;
			}
			String id = structures.create(schoolClass.getId(), installmentsOf(course, headIds), LATE_FINE).getId();
			structureIds.put(course.courseName(), id);
			summary.addFeeStructure();
		}
		return structureIds;
	}

	/** The five — or four — installments of one course. */
	private static List<Installment> installmentsOf(DemoCourse course, Map<String, String> headIds) {
		List<Installment> installments = new ArrayList<>();
		installments.add(semester(course.firstSemesterName(), headIds.get(SEMESTER_FEE), course.semesterFee(),
				DemoCalendar.SEMESTER_A_START, DemoCalendar.SEMESTER_A_END, DemoCalendar.SEMESTER_A_DUE));
		installments.add(semester(course.secondSemesterName(), headIds.get(SEMESTER_FEE), course.semesterFee(),
				DemoCalendar.SEMESTER_B_START, DemoCalendar.SEMESTER_B_END, DemoCalendar.SEMESTER_B_DUE));
		if (course.chargesAdmissionFee()) {
			installments.add(oneTime(ADMISSION_FEE, headIds.get(ADMISSION_FEE), course.admissionFee(),
					DemoCalendar.ADMISSION_FEE_DUE));
		}
		installments.add(oneTime(course.firstExamFeeName(), headIds.get(EXAM_FEE), course.examFee(),
				DemoCalendar.EXAM_FEE_A_DUE));
		installments.add(oneTime(course.secondExamFeeName(), headIds.get(EXAM_FEE), course.examFee(),
				DemoCalendar.EXAM_FEE_B_DUE));
		return installments;
	}

	/** A semester carries its period explicitly, and its label reads {@code "Semester 3 (Jul-Dec 2026)"}. */
	private static Installment semester(String name, String headId, long amount, LocalDate periodStart,
			LocalDate periodEnd, LocalDate dueDate) {
		String label = name + " (" + new FeePeriod(periodStart, periodEnd).describe() + ")";
		return new Installment(name, dueDate, List.of(new StructureItem(headId, amount)),
				InstallmentKind.SEMESTER, label, periodStart, periodEnd);
	}

	/** A one-off fee covers no period, so it carries none. */
	private static Installment oneTime(String name, String headId, long amount, LocalDate dueDate) {
		return new Installment(name, dueDate, List.of(new StructureItem(headId, amount)),
				InstallmentKind.ONE_TIME, name);
	}

	private void generateInvoices(Map<String, String> structureIds, DemoSummary summary) {
		structureIds.forEach((courseName, structureId) -> {
			InvoiceCounts counts = invoices.generateFor(structureId);
			summary.addInvoices(counts.created(), counts.skipped());
			log.info("Invoices for {}: {} created, {} already there", courseName, counts.created(),
					counts.skipped());
		});
	}

	// --- 4. the five payment patterns -------------------------------------------------------------

	/**
	 * Plans every course payment, pattern by pattern.
	 *
	 * <p>The pattern is {@code (rollNo - 1) % 5} <em>within one course and section</em>, so the data is
	 * the same every time it is seeded and so each section has a defaulter, a part payer and somebody
	 * who paid a year ahead. Nothing is taken here: these are returned to be sorted into date order
	 * with everything else.
	 */
	private List<PlannedPayment> planCoursePayments(String sessionId, Map<String, SchoolClass> courses,
			LocalDate today, DemoSummary summary) {
		List<PlannedPayment> plan = new ArrayList<>();
		for (DemoCourse course : DemoCourse.ALL) {
			SchoolClass schoolClass = courses.get(course.courseName());
			boolean examFeeTodayStillToGive = true;
			for (String section : sectionsOf(schoolClass)) {
				for (StudentRef student : students.activeInSection(schoolClass.getId(), section)) {
					int pattern = Math.floorMod(student.rollNo() - 1, PATTERNS);
					// The first "on time" student of each course pays an exam fee today instead of in
					// October, so the dashboard's "today's collection" is never zero.
					boolean examFeeToday = pattern == 0 && examFeeTodayStillToGive;
					if (examFeeToday) {
						examFeeTodayStillToGive = false;
					}
					plan.addAll(planFor(course, student, pattern, examFeeToday ? today : null, summary));
				}
			}
		}
		log.info("Planned {} course payments across {} courses", plan.size(), DemoCourse.ALL.size());
		return plan;
	}

	/**
	 * The payments one student makes.
	 *
	 * @param examFeeOn the date this student's first exam fee is paid on, or null for the pattern's own
	 *                  date. Only ever set for the first P0 student of a course
	 */
	private List<PlannedPayment> planFor(DemoCourse course, StudentRef student, int pattern, LocalDate examFeeOn,
			DemoSummary summary) {
		Map<String, Invoice> ledger = ledgerOf(student);
		String semesterFee = course.firstSemesterName();
		String admissionFee = course.chargesAdmissionFee() ? ADMISSION_FEE : null;
		GuardianNames guardians = students.guardianNamesOf(student.uniqueId());

		return switch (pattern) {
			case 0 -> onTime(course, student, ledger, semesterFee, admissionFee, guardians, examFeeOn);
			case 1 -> lateWithFine(student, ledger, semesterFee, admissionFee);
			case 2 -> partial(course, student, ledger, semesterFee, guardians);
			case 3 -> List.of();
			default -> inAdvance(course, student, ledger, semesterFee, admissionFee, guardians);
		};
	}

	/** P0: this semester and the admission fee before either falls due, then the first exam fee. */
	private List<PlannedPayment> onTime(DemoCourse course, StudentRef student, Map<String, Invoice> ledger,
			String semesterFee, String admissionFee, GuardianNames guardians, LocalDate examFeeOn) {
		String payer = payerName(guardians, true, student);
		List<PlannedPayment> planned = new ArrayList<>();
		List<String> fees = idsOf(ledger, semesterFee, admissionFee);
		if (!fees.isEmpty()) {
			planned.add(PlannedPayment.inFull(DemoCalendar.PAID_ON_TIME, student.uniqueId(), fees,
					PaymentMode.CASH, null, payer, PayerRelation.FATHER,
					student.uniqueId() + " on-time semester fee"));
		}
		List<String> examFee = idsOf(ledger, course.firstExamFeeName());
		if (!examFee.isEmpty()) {
			LocalDate paidOn = examFeeOn == null ? DemoCalendar.PAID_EXAM_FEE : examFeeOn;
			planned.add(PlannedPayment.inFull(paidOn, student.uniqueId(), examFee, PaymentMode.UPI_OFFLINE,
					reference("UTR", 10, student.uniqueId(), paidOn), payer, PayerRelation.FATHER,
					student.uniqueId() + " exam fee"));
		}
		return planned;
	}

	/**
	 * P1: the same fees, a month later, <strong>including the fine as of that day</strong>.
	 *
	 * <p>Nothing here states the fine. {@code markPaidInFull} quotes it from the invoices as of
	 * {@code paidOn}, which is the only way it could be right: ten days on the semester fee and
	 * twenty-one on the admission fee, both of which the seeder would otherwise have to work out.
	 */
	private List<PlannedPayment> lateWithFine(StudentRef student, Map<String, Invoice> ledger, String semesterFee,
			String admissionFee) {
		List<String> fees = idsOf(ledger, semesterFee, admissionFee);
		if (fees.isEmpty()) {
			return List.of();
		}
		return List.of(PlannedPayment.inFull(DemoCalendar.PAID_LATE, student.uniqueId(), fees,
				PaymentMode.UPI_OFFLINE, reference("UTR", 10, student.uniqueId(), DemoCalendar.PAID_LATE),
				student.name(), PayerRelation.SELF, student.uniqueId() + " late semester fee"));
	}

	/**
	 * P2: two part payments against the semester fee, leaving it short and the admission fee untouched.
	 *
	 * <p>The second one falls four weeks past the grace period, so the allocation settles the fine
	 * before the principal — which is what stops a family that pays something every month from
	 * carrying a fine that grows for the rest of the year.
	 */
	private List<PlannedPayment> partial(DemoCourse course, StudentRef student, Map<String, Invoice> ledger,
			String semesterFee, GuardianNames guardians) {
		List<String> fees = idsOf(ledger, semesterFee);
		if (fees.isEmpty()) {
			return List.of();
		}
		String payer = payerName(guardians, false, student);
		return List.of(
				PlannedPayment.ofAmount(DemoCalendar.PAID_PART_ONE, student.uniqueId(), fees,
						course.partialFirst(), PaymentMode.CASH, null, payer, PayerRelation.MOTHER,
						student.uniqueId() + " first part payment"),
				PlannedPayment.ofAmount(DemoCalendar.PAID_PART_TWO, student.uniqueId(), fees,
						course.partialSecond(), PaymentMode.CHEQUE,
						reference("CHQ", 6, student.uniqueId(), DemoCalendar.PAID_PART_TWO), payer,
						PayerRelation.MOTHER, student.uniqueId() + " second part payment"));
	}

	/** P4: both semesters and the admission fee in one transfer, with the first semester's fine capped. */
	private List<PlannedPayment> inAdvance(DemoCourse course, StudentRef student, Map<String, Invoice> ledger,
			String semesterFee, String admissionFee, GuardianNames guardians) {
		List<String> fees = idsOf(ledger, semesterFee, course.secondSemesterName(), admissionFee);
		if (fees.isEmpty()) {
			return List.of();
		}
		return List.of(PlannedPayment.inFull(DemoCalendar.PAID_IN_ADVANCE, student.uniqueId(), fees,
				PaymentMode.BANK_TRANSFER,
				reference("NEFT", 8, student.uniqueId(), DemoCalendar.PAID_IN_ADVANCE),
				payerName(guardians, true, student), PayerRelation.FATHER,
				student.uniqueId() + " both semesters in advance"));
	}

	// --- 5. the three individual charges ----------------------------------------------------------

	/**
	 * Raises the three named charges, and plans the one of them that gets paid.
	 *
	 * <p>A charge is a {@code CUSTOM} invoice with no structure behind it, which means no late fine
	 * ever reaches it — the certificate fee paid two days after its due date is paid in full at face
	 * value, and the two library fines simply sit on the defaulters list.
	 */
	private List<PlannedPayment> seedIndividualCharges(String sessionId, Map<String, SchoolClass> courses,
			Map<String, String> headIds, DemoSummary summary) {
		List<PlannedPayment> planned = new ArrayList<>();

		raiseCharge(sessionId, courses, "Rahul Verma", "BA - Year 1", LIBRARY_FINE, headIds.get(LIBRARY_FINE),
				150_00L, DemoCalendar.LIBRARY_FINE_DUE, summary);

		Optional<Invoice> certificate = raiseCharge(sessionId, courses, "Anjali Yadav", "BA - Year 2",
				CERTIFICATE_FEE, headIds.get(CERTIFICATE_FEE), 200_00L, DemoCalendar.CERTIFICATE_FEE_DUE, summary);
		certificate.ifPresent(invoice -> {
			StudentRef student = students.requireRef(invoice.getStudentUniqueId());
			planned.add(PlannedPayment.inFull(DemoCalendar.CERTIFICATE_FEE_PAID, student.uniqueId(),
					List.of(invoice.getId()), PaymentMode.CASH, null, student.name(), PayerRelation.SELF,
					student.uniqueId() + " certificate fee"));
		});

		raiseCharge(sessionId, courses, "Shivani Ojha", "BTC - Year 1", LIBRARY_FINE, headIds.get(LIBRARY_FINE),
				100_00L, DemoCalendar.BTC_LIBRARY_FINE_DUE, summary);

		return planned;
	}

	/** @return the invoice raised, or empty when that student is not on this course's roll */
	private Optional<Invoice> raiseCharge(String sessionId, Map<String, SchoolClass> courses, String studentName,
			String courseName, String chargeName, String headId, long amount, LocalDate dueDate,
			DemoSummary summary) {
		Optional<StudentRef> student = findByName(sessionId, courses, courseName, studentName);
		if (student.isEmpty()) {
			summary.warn("No active student called " + studentName + " on " + courseName + ", so the "
					+ chargeName.toLowerCase(Locale.ENGLISH) + " was not raised.");
			return Optional.empty();
		}
		Invoice raised = charges.raiseOne(student.get().uniqueId(), chargeName, headId, amount, dueDate, null);
		summary.addIndividualCharge();
		return Optional.of(raised);
	}

	// --- taking the money -------------------------------------------------------------------------

	/**
	 * Takes every planned payment, oldest first.
	 *
	 * <p>A payment that is refused is warned about and the rest go on. That is not laxity: the one way
	 * it happens is a ledger that is not what this data set expects — a structure somebody had already
	 * created by hand, an invoice already settled — and stopping the run halfway would leave a worse
	 * mess than finishing it and saying what was skipped.
	 */
	private void execute(List<PlannedPayment> plan, DemoSummary summary) {
		List<PlannedPayment> inDateOrder = plan.stream().sorted(PlannedPayment.IN_DATE_ORDER).toList();
		for (PlannedPayment planned : inDateOrder) {
			try {
				Payment taken = planned.settlesInFull()
						? payments.markPaidInFull(planned.invoiceIds(), planned.mode(), planned.reference(),
								planned.payerName(), planned.payerRelation(), planned.paidOn())
						: payments.recordOffline(planned.studentUniqueId(), planned.invoiceIds(),
								planned.amount(), planned.mode(), planned.reference(), planned.payerName(),
								planned.payerRelation(), planned.paidOn());
				summary.addPayment(taken.getMode(), taken.getAmount(), taken.getReceiptNo());
			}
			catch (AppException ex) {
				summary.warn("The payment for " + planned.what() + " on " + planned.paidOn()
						+ " was refused: " + ex.getMessage());
			}
		}
		log.info("Took {} of {} planned payments, {} in total", summary.payments(), inDateOrder.size(),
				summary.totalCollected());
	}

	// --- 6. the one withdrawn student -------------------------------------------------------------

	private void withdrawStudent(String sessionId, Map<String, SchoolClass> courses, DemoSummary summary) {
		String name = "Manish Chaurasia";
		String courseName = "BA - Year 2";
		Optional<StudentRef> student = findByName(sessionId, courses, courseName, name);
		if (student.isEmpty()) {
			summary.warn("No active student called " + name + " on " + courseName + ", so nobody was marked "
					+ "as having left on " + DemoCalendar.LEFT_ON + ".");
			return;
		}
		students.markLeft(student.get().uniqueId());
		summary.addStudentMarkedLeft();
		log.info("{} was marked as having left as of {}", student.get().uniqueId(), DemoCalendar.LEFT_ON);
	}

	// --- internals --------------------------------------------------------------------------------

	/** This student's invoices keyed by installment name, which is what every pattern selects on. */
	private Map<String, Invoice> ledgerOf(StudentRef student) {
		Map<String, Invoice> ledger = new LinkedHashMap<>();
		invoices.invoicesOf(student.uniqueId())
				.forEach(invoice -> ledger.putIfAbsent(invoice.getInstallmentName(), invoice));
		return ledger;
	}

	/** The ids of the named installments that are on this ledger. Nulls and misses are dropped. */
	private static List<String> idsOf(Map<String, Invoice> ledger, String... installmentNames) {
		List<String> ids = new ArrayList<>(installmentNames.length);
		for (String name : installmentNames) {
			Invoice invoice = name == null ? null : ledger.get(name);
			if (invoice != null) {
				ids.add(invoice.getId());
			}
		}
		return ids;
	}

	/** Every section of a course, in the order the office listed them. */
	private static List<String> sectionsOf(SchoolClass schoolClass) {
		return schoolClass.getSections() == null ? List.of() : schoolClass.getSections();
	}

	/**
	 * Who the receipt is made out to.
	 *
	 * <p>The parent's name as recorded, falling back to the guardian, and then to a form of words that
	 * is at least honest: there are no parent accounts in this system, so this field is free text and
	 * a blank one on a receipt is worse than a described one.
	 */
	private static String payerName(GuardianNames guardians, boolean father, StudentRef student) {
		String name = guardians.parentOrGuardian(father);
		if (name != null) {
			return name;
		}
		return (father ? "Father of " : "Mother of ") + student.name();
	}

	/**
	 * A transfer reference that is made up but stable: {@code UTR0123456789}.
	 *
	 * <p>Derived from the student and the date rather than drawn at random, because the specification
	 * asks for data that comes out the same every time it is seeded — and because a reference that
	 * changed on each run would make two demonstration databases impossible to compare.
	 */
	private static String reference(String prefix, int digits, String studentUniqueId, LocalDate paidOn) {
		long modulus = 1L;
		for (int i = 0; i < digits; i++) {
			modulus *= 10L;
		}
		long value = Integer.toUnsignedLong((studentUniqueId + "|" + paidOn).hashCode()) % modulus;
		return prefix + String.format(Locale.ROOT, "%0" + digits + "d", value);
	}

	/** The one active student of that course with that name, if there is one. */
	private Optional<StudentRef> findByName(String sessionId, Map<String, SchoolClass> courses, String courseName,
			String studentName) {
		SchoolClass schoolClass = courses.get(courseName);
		if (schoolClass == null) {
			return Optional.empty();
		}
		return students.activeInClass(sessionId, schoolClass.getId()).stream()
				.filter(student -> studentName.equalsIgnoreCase(student.name()))
				.findFirst();
	}
}

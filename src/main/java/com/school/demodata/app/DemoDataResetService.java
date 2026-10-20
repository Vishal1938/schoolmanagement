package com.school.demodata.app;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.school.academics.app.AcademicContext;
import com.school.academics.domain.AcademicSession;
import com.school.attendance.app.AttendanceDataResetService;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.BusinessRuleException;
import com.school.fees.app.FeeDataResetService;
import com.school.fees.app.InvoiceService;
import com.school.fees.domain.Invoice;
import com.school.payment.app.PaymentDataResetService;
import com.school.payroll.app.PayrollDataResetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Wipes the demonstration data set for the active session.
 *
 * <p>Each module deletes its own collections; this only decides the order and adds the counts up.
 * <strong>The order is the whole design.</strong> Payments are deleted before the invoices they point
 * at, because a payment is found <em>through</em> those invoices — nothing on a payment says which
 * session it belongs to, and once the invoices are gone the payments are unreachable. Receipt PDFs go
 * with their payments, and the receipt counter is reset once nothing is numbered from it.
 *
 * <p><strong>What it never touches:</strong> students, employees, user accounts, courses, subjects,
 * sessions, fee heads and the school configuration. Those are the institution's own records — the
 * seeder did not create them and asks for them to exist — so a wipe leaves a college that can be
 * seeded again rather than an empty database.
 *
 * <p>Two things it deliberately does not undo. The {@code LEFT} status of the one withdrawn student
 * and the one departed employee <em>stays</em>, because this does not touch people at all; re-seeding
 * will warn that neither is on the roll any more, which is honest. And a holiday or a fee head that
 * the institution had declared itself before seeding is removed along with the seeded ones in the
 * holidays' case and kept in the heads' — holidays are dated and inside the session, heads are not.
 */
@Service
@ConditionalOnProperty(prefix = "app.demo-data", name = "enabled", havingValue = "true")
public class DemoDataResetService {

	private static final String AUDIT_ENTITY = "DemoData";

	private static final Logger log = LoggerFactory.getLogger(DemoDataResetService.class);

	private final AcademicContext academicContext;
	private final InvoiceService invoices;
	private final PaymentDataResetService paymentReset;
	private final FeeDataResetService feeReset;
	private final PayrollDataResetService payrollReset;
	private final AttendanceDataResetService attendanceReset;
	private final AuditService audit;

	public DemoDataResetService(AcademicContext academicContext, InvoiceService invoices,
			PaymentDataResetService paymentReset, FeeDataResetService feeReset,
			PayrollDataResetService payrollReset, AttendanceDataResetService attendanceReset, AuditService audit) {
		this.academicContext = academicContext;
		this.invoices = invoices;
		this.paymentReset = paymentReset;
		this.feeReset = feeReset;
		this.payrollReset = payrollReset;
		this.attendanceReset = attendanceReset;
		this.audit = audit;
	}

	/**
	 * Deletes everything the seeder writes, for the active session.
	 *
	 * <p>Not transactional. It spans five collections in four modules and would be far too much for one
	 * MongoDB transaction to hold; a wipe that fails part-way is safe, because every step is "remove
	 * what is still there" and running it again finishes the job.
	 *
	 * @return what was deleted, per collection
	 */
	public Map<String, Object> reset() {
		AcademicSession session = academicContext.currentSession();
		LocalDate from = requireDate(session.getStartDate(), session, "startDate");
		LocalDate to = requireDate(session.getEndDate(), session, "endDate");

		// Gathered before anything is deleted: this is the only link between a payment and a session.
		List<String> invoiceIds = invoices.invoicesInSession(session.getId()).stream()
				.map(Invoice::getId)
				.toList();

		Map<String, Object> deleted = new LinkedHashMap<>();
		deleted.putAll(paymentReset.purgeForInvoices(invoiceIds).asCounts());
		deleted.putAll(feeReset.purgeSession(session.getId()).asCounts());
		deleted.putAll(payrollReset.purge(YearMonth.from(from), YearMonth.from(to)).asCounts());
		deleted.putAll(attendanceReset.purge(from, to).asCounts());

		audit.record(AuditAction.DEMO_DATA_RESET, AUDIT_ENTITY, session.getId(), null, deleted);
		log.info("Wiped the demonstration data set from session {}: {}", session.getName(), deleted);
		return deleted;
	}

	/**
	 * A session with no dates cannot be wiped: the payroll months and the attendance range are both
	 * derived from them, and guessing would delete the wrong year.
	 */
	private static LocalDate requireDate(LocalDate date, AcademicSession session, String field) {
		if (date == null) {
			throw new BusinessRuleException("The active session \"" + session.getName() + "\" has no " + field
					+ ", so there is no range to wipe. Set its dates through PUT /sessions first.");
		}
		return date;
	}
}

package com.school.payment.app;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

import com.school.academics.app.SchoolClassService;
import com.school.academics.domain.SchoolClass;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.BusinessRuleException;
import com.school.common.storage.ObjectStorage;
import com.school.payment.domain.Payment;
import com.school.payment.domain.PaymentStatus;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import com.school.schoolconfig.app.SchoolConfigService;
import org.springframework.stereotype.Service;

/**
 * The receipt PDF: who may have one, and making sure it is only ever rendered once.
 *
 * <p>The first download renders it and stores it under {@code receipts/}; every download after that
 * serves the stored bytes. The key is derived from the receipt number, so no extra field is needed on
 * the payment and the whole thing is idempotent — two simultaneous first downloads both render,
 * both write the same key, and both return the same document.
 *
 * <p>Rendering lazily rather than at capture time is deliberate: writing to object storage inside the
 * payment transaction would orphan a file on every rollback, and would make taking money at the
 * counter depend on the object store being up.
 */
@Service
public class ReceiptService {

	/** Key prefix. Deliberately outside {@code app.storage.public-prefix}: a receipt names amounts. */
	static final String PREFIX = "receipts/";

	private final PaymentService payments;
	private final StudentService students;
	private final SchoolClassService classes;
	private final SchoolConfigService schoolConfig;
	private final ObjectStorage storage;
	private final ReceiptPdf pdf;
	private final AuditService audit;
	private final Clock clock;

	public ReceiptService(PaymentService payments, StudentService students, SchoolClassService classes,
			SchoolConfigService schoolConfig, ObjectStorage storage, ReceiptPdf pdf, AuditService audit, Clock clock) {
		this.payments = payments;
		this.students = students;
		this.classes = classes;
		this.schoolConfig = schoolConfig;
		this.storage = storage;
		this.pdf = pdf;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * The receipt for one payment — for an admin, or the student it is for.
	 *
	 * @throws BusinessRuleException 422 for a payment that was never captured. There is nothing to
	 *                               receipt until the money is in
	 */
	public Receipt forPayment(String paymentId) {
		Payment payment = payments.requireReadable(paymentId);
		if (payment.getStatus() != PaymentStatus.CAPTURED || payment.getReceiptNo() == null) {
			throw new BusinessRuleException("This payment has not been captured, so it has no receipt yet. "
					+ "Its status is " + payment.getStatus() + ".");
		}

		String key = PREFIX + payment.getReceiptNo() + ".pdf";
		String fileName = fileName(payment);
		return storage.find(key)
				.map(stored -> new Receipt(fileName, stored.bytes()))
				.orElseGet(() -> new Receipt(fileName, renderAndStore(payment, key)));
	}

	/**
	 * Renders and stores the receipt for a payment that has just been settled, unless it is stored
	 * already.
	 *
	 * <p><strong>No authorization check, and none is missing.</strong> This is not reachable from a
	 * request: it is called by {@link OnlinePaymentService} immediately after the settlement
	 * transaction commits, from a path that may have no security context at all — a webhook from the
	 * gateway, or the reconciliation job. Nothing is returned, so the caller cannot read the bytes;
	 * they are only ever served through {@link #forPayment}, which does check.
	 *
	 * <p>Rendering here rather than waiting for a download is what makes an online payment feel
	 * finished: the family is looking at the page when the webhook lands. Doing it <em>after</em> the
	 * commit rather than inside it is what keeps a rollback from orphaning a file in the object store.
	 */
	public void ensureRendered(Payment payment) {
		if (payment.getStatus() != PaymentStatus.CAPTURED || payment.getReceiptNo() == null) {
			return;
		}
		String key = PREFIX + payment.getReceiptNo() + ".pdf";
		if (storage.find(key).isPresent()) {
			return;
		}
		renderAndStore(payment, key);
	}

	private byte[] renderAndStore(Payment payment, String key) {
		byte[] bytes = pdf.render(gather(payment));
		storage.put(key, bytes, "application/pdf", Map.of("receiptno", payment.getReceiptNo()));
		// Recorded once per receipt rather than per download: this is the moment the document that
		// gets handed to a family comes into existence.
		audit.record(AuditAction.RECEIPT_GENERATED, PaymentService.AUDIT_ENTITY, payment.getId(),
				"receipt " + payment.getReceiptNo());
		return bytes;
	}

	/** Everything the renderer needs, read here so the renderer itself touches nothing. */
	private ReceiptData gather(Payment payment) {
		StudentRef student = students.findRef(payment.getStudentUniqueId()).orElse(null);
		String className = student == null ? null : classes.findById(student.classId())
				.map(SchoolClass::getName)
				.orElse(null);
		return new ReceiptData(
				schoolConfig.identity(),
				schoolConfig.contact(),
				payment,
				// A student whose record has gone still gets a reprintable receipt: the payment carries
				// the uniqueId, which is what the document is really keyed on.
				student == null ? null : student.name(),
				className,
				student == null ? null : student.section(),
				LocalDate.ofInstant(payment.getPaidAt(), clock.getZone()),
				schoolConfig.receiptFooter());
	}

	/** {@code receipt-DVM-RCP-26-000001.pdf}. */
	private static String fileName(Payment payment) {
		String safe = payment.getReceiptNo().toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("^-|-$", "");
		return "receipt-" + safe + ".pdf";
	}
}

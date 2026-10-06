package com.school.fees.infra;

import java.util.Collection;
import java.util.List;

import com.school.fees.domain.Invoice;
import com.school.fees.domain.InvoiceStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Repository for {@code fee_invoices}. Internal to this module; callers use {@code InvoiceService}. */
public interface InvoiceRepository extends MongoRepository<Invoice, String> {

	/** One student's ledger, oldest installment first — the order a statement reads in. */
	List<Invoice> findByStudentUniqueIdOrderByDueDateAsc(String studentUniqueId);

	/**
	 * The same for a page of students at once, which is what makes the {@code feeStatus} column on the
	 * student list one query rather than one per row.
	 */
	List<Invoice> findByStudentUniqueIdIn(Collection<String> studentUniqueIds);

	List<Invoice> findByStudentUniqueIdAndSessionIdAndStatus(String studentUniqueId, String sessionId,
			InvoiceStatus status);

	/** Used to refuse an edit that would rename an installment out from under its invoices. */
	List<Invoice> findByStructureId(String structureId);

	/** The invoices a payment names, in one read. */
	List<Invoice> findAllByIdIn(Collection<String> ids);

	/** Backs the defaulters report: everything still owed in a session, optionally for one class. */
	List<Invoice> findBySessionIdAndStatusIn(String sessionId, Collection<InvoiceStatus> statuses);

	List<Invoice> findBySessionIdAndClassIdAndStatusIn(String sessionId, String classId,
			Collection<InvoiceStatus> statuses);
}

package com.school.fees.app;

import java.util.List;
import java.util.Map;

import com.school.fees.domain.Concession;
import com.school.fees.domain.FeeStructure;
import com.school.fees.domain.Invoice;
import com.school.fees.infra.ConcessionRepository;
import com.school.fees.infra.FeeStructureRepository;
import com.school.fees.infra.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Wipes one session's fee records — structures, invoices and concessions.
 *
 * <p>This exists so that the demonstration data reset (B20) can start a session over without
 * reaching into this module's repositories, which CLAUDE.md forbids and which would also mean two
 * places knowing that a concession is keyed on the session rather than on the structure.
 *
 * <p><strong>It deletes rather than cancels, and that is the opposite of this module's usual rule.</strong>
 * {@code CANCELLED} exists because receipts and reports point at invoices; the only caller here
 * deletes the payments and the receipt PDFs in the same operation, so after it nothing points at
 * anything. Fee heads are deliberately left alone — a head is not demonstration data, it is what the
 * institution charges for, and invoices raised next term will name the same ones.
 */
@Service
public class FeeDataResetService {

	private static final Logger log = LoggerFactory.getLogger(FeeDataResetService.class);

	private final FeeStructureRepository structures;
	private final InvoiceRepository invoices;
	private final ConcessionRepository concessions;

	public FeeDataResetService(FeeStructureRepository structures, InvoiceRepository invoices,
			ConcessionRepository concessions) {
		this.structures = structures;
		this.invoices = invoices;
		this.concessions = concessions;
	}

	/**
	 * Removes every structure, invoice and concession of one session.
	 *
	 * <p>Not transactional. Three collections of a few hundred rows each is more than one MongoDB
	 * transaction wants to hold open, and a half-finished wipe is safe: running it again finishes the
	 * job, because every delete is "remove what is still there".
	 */
	public FeeDataPurge purgeSession(String sessionId) {
		List<Invoice> billed = invoices.findBySessionId(sessionId);
		List<FeeStructure> raised = structures.findBySessionIdOrderByClassIdAsc(sessionId);
		List<Concession> granted = concessions.findBySessionIdOrderByCreatedAtDesc(sessionId);

		invoices.deleteAll(billed);
		structures.deleteAll(raised);
		concessions.deleteAll(granted);

		FeeDataPurge purge = new FeeDataPurge(raised.size(), billed.size(), granted.size());
		log.info("Wiped the fee records of session {}: {}", sessionId, purge);
		return purge;
	}

	/** What a wipe removed, per collection. */
	public record FeeDataPurge(int feeStructures, int invoices, int concessions) {

		/** For the response and the audit entry, where the keys are the collection names. */
		public Map<String, Integer> asCounts() {
			return Map.of("feeStructures", feeStructures, "invoices", invoices, "concessions", concessions);
		}
	}
}

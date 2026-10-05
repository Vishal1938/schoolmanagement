package com.school.fees.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * What one student owes for one installment.
 *
 * <p><strong>The late fine is not in here.</strong> It is a function of today's date, so storing it
 * would mean a figure that is wrong by tomorrow; it is computed from the structure's
 * {@link LateFineRule} whenever the invoice is read, and returned as {@code lateFineDue}. What *is*
 * stored is {@link #lateFinePaid}, because money actually taken is a fact rather than a calculation.
 *
 * <p>The items and the concession are snapshots. Editing the structure or adding a concession later
 * does not rewrite an invoice that already exists — {@code POST /fees/concessions/{id}/apply} is the
 * deliberate, audited way to do that, and only for invoices nothing has been paid against.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(Invoice.COLLECTION)
// This is what makes generate-invoices idempotent: running it twice hits this index on every row it
// already wrote, rather than billing the school's students twice.
@CompoundIndex(name = "fee_invoices_student_structure_installment_idx",
		def = "{'studentUniqueId': 1, 'structureId': 1, 'installmentName': 1}", unique = true)
// The per-student ledger and the fee status, which is every read on this collection that matters.
@CompoundIndex(name = "fee_invoices_student_idx", def = "{'studentUniqueId': 1, 'status': 1}")
// Class-wide reads: the defaulters list and the dashboards.
@CompoundIndex(name = "fee_invoices_session_class_idx",
		def = "{'sessionId': 1, 'classId': 1, 'status': 1}")
public class Invoice {

	public static final String COLLECTION = "fee_invoices";

	@Id
	private String id;

	/** The student, by the id that never changes — not the Mongo document id. */
	private String studentUniqueId;

	/** The class as of generation. Kept so a class-wide report needs no join. */
	private String classId;

	private String sessionId;

	/** The structure this came from, and where the live late-fine rule is read from. */
	private String structureId;

	/** Names the installment of that structure. Part of the idempotency key. */
	private String installmentName;

	private LocalDate dueDate;

	/** The heads charged, with the name they had when it was charged. */
	private List<InvoiceItem> items;

	/** Total concession applied, in paise. Never more than the sum of the items. */
	private long concessionAmount;

	/** Sum of the items less {@link #concessionAmount}. What is actually owed, before any fine. */
	private long netAmount;

	/** Principal taken so far, in paise. B12 moves this. */
	private long paidAmount;

	/** Late fine actually collected, in paise. Separate from the principal so reports can split them. */
	private long lateFinePaid;

	private InvoiceStatus status;

	private Instant createdAt;

	private Instant updatedAt;

	/** What is left of the principal, in paise. Zero for a cancelled invoice. */
	public long outstanding() {
		return status == InvoiceStatus.CANCELLED ? 0L : Math.max(0L, netAmount - paidAmount);
	}

	/** Sum of the items before any concession, in paise. */
	public long grossAmount() {
		return items == null ? 0L : items.stream().mapToLong(InvoiceItem::amount).sum();
	}
}

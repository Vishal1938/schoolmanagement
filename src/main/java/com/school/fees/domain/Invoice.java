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
// already wrote, rather than billing the school's students twice. A CUSTOM charge has a null
// structureId, so the same index also stops one student being charged "Late library fine" twice
// under one name — StudentChargeService suffixes the name rather than colliding.
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

	/**
	 * The structure this came from, and where the live late-fine rule is read from.
	 *
	 * <p>Null for a {@link InstallmentKind#CUSTOM} charge, which is raised against one student and
	 * belongs to no structure — and therefore carries no late fine, because there is no rule to read.
	 */
	private String structureId;

	/** Names the installment of that structure. Part of the idempotency key. */
	private String installmentName;

	/**
	 * What sort of charge this is. Null on an invoice raised before this field existed, which reads as
	 * {@link InstallmentKind#MONTHLY} — read it through {@link #resolvedKind()}, never off the field.
	 */
	private InstallmentKind kind;

	/**
	 * How to show it — a register column heading, a receipt line. Snapshotted from the installment at
	 * generation for the same reason the head names are: renaming a column next year must not restate
	 * a receipt already issued. Null means "use {@link #installmentName}".
	 */
	private String label;

	/** Free text an admin typed when raising a CUSTOM charge, e.g. which book was lost. Null otherwise. */
	private String note;

	private LocalDate dueDate;

	/**
	 * First day of the session this invoice's installment covers, snapshotted at generation.
	 *
	 * <p>Null on an invoice raised before this field existed and on one raised from a one-off or
	 * individual charge, which covers no period. Read it through {@link #period()}, which backfills a
	 * MONTHLY invoice's period from its due date.
	 */
	private LocalDate periodStart;

	/** Last day covered, inclusive. Same rules as {@link #periodStart}. */
	private LocalDate periodEnd;

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

	/** The kind, where a missing one means MONTHLY. See {@link InstallmentKind}. */
	public InstallmentKind resolvedKind() {
		return kind == null ? InstallmentKind.MONTHLY : kind;
	}

	/** What to display: the label if one was snapshotted, otherwise the installment name. */
	public String displayLabel() {
		return label == null || label.isBlank() ? installmentName : label;
	}

	/**
	 * The stretch of the session this invoice covers, or null when it covers none.
	 *
	 * <p>Backfilled exactly as {@link Installment#period()} is: a MONTHLY invoice with no stored period
	 * — every one raised before B19 — covers the calendar month of its due date. A SEMESTER invoice
	 * always carries its own, because a semester's period cannot be inferred from a date.
	 */
	public FeePeriod period() {
		if (periodStart != null && periodEnd != null) {
			return new FeePeriod(periodStart, periodEnd);
		}
		if (resolvedKind() == InstallmentKind.MONTHLY && dueDate != null) {
			return FeePeriod.ofMonthContaining(dueDate);
		}
		return null;
	}

	/** First day covered, backfilled as {@link #period()} describes. */
	public LocalDate resolvedPeriodStart() {
		FeePeriod period = period();
		return period == null ? null : period.start();
	}

	/** Last day covered, inclusive, backfilled as {@link #period()} describes. */
	public LocalDate resolvedPeriodEnd() {
		FeePeriod period = period();
		return period == null ? null : period.end();
	}

	/**
	 * A recurring invoice — MONTHLY or SEMESTER — whose period contains that date. This is the single
	 * definition of "the installment covering today", shared by the dashboard and the register.
	 */
	public boolean coversRecurring(LocalDate date) {
		if (!resolvedKind().isRecurring()) {
			return false;
		}
		FeePeriod period = period();
		return period != null && period.covers(date);
	}

	/** Nothing has been taken against this invoice, as fee or as fine — the test a charge must pass to be deleted. */
	public boolean untouched() {
		return paidAmount == 0L && lateFinePaid == 0L;
	}
}

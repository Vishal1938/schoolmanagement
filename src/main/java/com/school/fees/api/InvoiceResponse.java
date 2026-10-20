package com.school.fees.api;

import java.time.LocalDate;
import java.util.List;

import com.school.fees.domain.InstallmentKind;
import com.school.fees.domain.Invoice;
import com.school.fees.domain.InvoiceItem;
import com.school.fees.domain.InvoiceStatus;

/**
 * One invoice as the API returns it. All amounts are paise.
 *
 * @param label       what to show for it — the installment's label, or its name when there is none
 * @param kind        MONTHLY, SEMESTER, ONE_TIME or CUSTOM; always present, since an invoice stored
 *                    without one is reported as the MONTHLY it is taken to be
 * @param note        free text an admin typed when raising an individual charge; null otherwise
 * @param periodStart first day of the session this covers, and {@code periodEnd} the last, inclusive.
 *                    Both null for a one-off or individual charge, which covers no period. A MONTHLY
 *                    invoice raised before this existed reports the calendar month of its due date
 *                    rather than null
 * @param lateFineDue what the fine comes to <em>right now</em>, computed on read from the structure's
 *                    rule and never stored. 0 once the invoice is settled or cancelled, and always 0
 *                    for a CUSTOM charge, which belongs to no structure and so has no rule
 * @param balance     {@code netAmount - paidAmount + lateFineDue}: the figure to collect today
 * @param overdue     past the due date plus the grace days with something still owed
 */
public record InvoiceResponse(
		String id,
		String studentUniqueId,
		String classId,
		String sessionId,
		String structureId,
		String installmentName,
		String label,
		InstallmentKind kind,
		String note,
		LocalDate dueDate,
		LocalDate periodStart,
		LocalDate periodEnd,
		List<InvoiceItem> items,
		long grossAmount,
		long concessionAmount,
		long netAmount,
		long paidAmount,
		long lateFinePaid,
		long lateFineDue,
		long balance,
		boolean overdue,
		InvoiceStatus status) {

	public static InvoiceResponse of(Invoice invoice, long lateFineDue, boolean overdue) {
		return new InvoiceResponse(
				invoice.getId(),
				invoice.getStudentUniqueId(),
				invoice.getClassId(),
				invoice.getSessionId(),
				invoice.getStructureId(),
				invoice.getInstallmentName(),
				invoice.displayLabel(),
				invoice.resolvedKind(),
				invoice.getNote(),
				invoice.getDueDate(),
				invoice.resolvedPeriodStart(),
				invoice.resolvedPeriodEnd(),
				invoice.getItems() == null ? List.of() : invoice.getItems(),
				invoice.grossAmount(),
				invoice.getConcessionAmount(),
				invoice.getNetAmount(),
				invoice.getPaidAmount(),
				invoice.getLateFinePaid(),
				lateFineDue,
				invoice.outstanding() + lateFineDue,
				overdue,
				invoice.getStatus());
	}
}

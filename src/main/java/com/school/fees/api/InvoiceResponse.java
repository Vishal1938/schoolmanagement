package com.school.fees.api;

import java.time.LocalDate;
import java.util.List;

import com.school.fees.domain.Invoice;
import com.school.fees.domain.InvoiceItem;
import com.school.fees.domain.InvoiceStatus;

/**
 * One invoice as the API returns it. All amounts are paise.
 *
 * @param lateFineDue what the fine comes to <em>right now</em>, computed on read from the structure's
 *                    rule and never stored. 0 once the invoice is settled or cancelled
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
		LocalDate dueDate,
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
				invoice.getDueDate(),
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

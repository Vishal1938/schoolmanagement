package com.school.payment.app;

import java.time.LocalDate;

import com.school.payment.domain.Payment;
import com.school.schoolconfig.domain.ContactDetails;
import com.school.schoolconfig.domain.Identity;

/**
 * Everything one receipt prints, gathered before a single byte of PDF is written.
 *
 * <p>{@link ReceiptPdf} takes this and nothing else — it reads no database and calls no service — so
 * who may see a receipt is settled in {@link ReceiptService}, where the authorization is, and not
 * somewhere inside a layout routine.
 *
 * @param footer the configured receipt footer; may be null, and the receipt is never refused over it
 */
public record ReceiptData(
		Identity identity,
		ContactDetails contact,
		Payment payment,
		String studentName,
		String className,
		String section,
		LocalDate paidOn,
		String footer) {
}

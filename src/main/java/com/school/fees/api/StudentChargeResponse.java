package com.school.fees.api;

import java.util.List;

/**
 * What {@code POST /fees/charges} raised: one invoice per student named.
 *
 * <p>The invoice ids come back so a clerk can take the money for them straight away through
 * {@code POST /fees/register/mark-paid} or {@code POST /fees/offline-payment} without looking them up
 * again.
 *
 * @param created how many invoices were raised; always the number of students named, because the
 *                request is rejected outright if any of them is unknown
 */
public record StudentChargeResponse(int created, List<Charge> charges) {

	/**
	 * @param installmentName the name actually stored, which is {@code name} with a numeric suffix when
	 *                        that student already had a charge of the same name. The display
	 *                        {@code label} is always what was asked for
	 */
	public record Charge(
			String invoiceId,
			String studentUniqueId,
			String studentName,
			String installmentName,
			String label,
			long amount) {
	}
}

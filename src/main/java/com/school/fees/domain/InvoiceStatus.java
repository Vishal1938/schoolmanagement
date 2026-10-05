package com.school.fees.domain;

/** Where one invoice stands. Derived from {@code paidAmount} against {@code netAmount}, except for CANCELLED. */
public enum InvoiceStatus {

	/** Nothing paid against it. */
	UNPAID,

	/** Part paid. */
	PARTIAL,

	/** Settled in full. */
	PAID,

	/**
	 * Withdrawn — a student who left mid-year, or an invoice raised by mistake. Invoices are never
	 * deleted, because receipts and reports point at them, so this is how one stops counting. A
	 * cancelled invoice is left out of every total and accrues no late fine.
	 */
	CANCELLED
}

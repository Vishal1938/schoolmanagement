package com.school.accounts.domain;

/**
 * How one entry's money moved.
 *
 * <p>Its own enum rather than the fees {@code PaymentMode} or the payroll
 * {@code SalaryPaymentMode}: this one has to cover both directions and has no {@code ONLINE} case —
 * a gateway collection arrives in a bank account, and what the books record is that it is a bank
 * transfer. The two automatic writers map their own mode onto this one.
 */
public enum EntryMode {

	CASH,

	UPI,

	/** {@code reference} is the UTR. What a gateway collection and a bank run are both recorded as. */
	BANK_TRANSFER,

	/** {@code reference} is the cheque number. */
	CHEQUE
}

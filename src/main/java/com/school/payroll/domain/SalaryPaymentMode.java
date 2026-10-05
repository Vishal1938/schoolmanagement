package com.school.payroll.domain;

/**
 * How a salary was paid out.
 *
 * <p>Separate from the fees {@code PaymentMode}, which is money coming in through a gateway: a
 * salary never has an ONLINE case, and the two lists should be free to diverge.
 */
public enum SalaryPaymentMode {

	/** {@code reference} is the UTR. The usual case, and what pay-bulk is for. */
	BANK_TRANSFER,

	CASH,

	/** {@code reference} is the cheque number. */
	CHEQUE,

	/** {@code reference} is the UPI transaction reference. */
	UPI
}

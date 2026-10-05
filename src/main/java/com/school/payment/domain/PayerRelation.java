package com.school.payment.domain;

/**
 * Who paid, in relation to the student.
 *
 * <p>This exists because there are no parent accounts in this system: a parent uses the student's
 * login, so the only way the receipt can name the person who actually handed over the money is to
 * ask. It is printed as "Paid by: {payerName} ({relation})" and is not an account of any kind.
 */
public enum PayerRelation {

	/** The student themselves. */
	SELF,

	FATHER,

	MOTHER,

	GUARDIAN,

	OTHER
}

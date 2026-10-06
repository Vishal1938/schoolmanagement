package com.school.fees.domain;

/** How a concession's {@code value} is read. */
public enum ConcessionType {

	/** {@code value} is a whole percentage, 1–100, of the heads it applies to. */
	PERCENT,

	/** {@code value} is an amount in paise, taken off each installment it applies to. */
	FIXED
}

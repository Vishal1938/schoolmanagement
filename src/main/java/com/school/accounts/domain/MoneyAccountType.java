package com.school.accounts.domain;

/** Where the school's money physically is. */
public enum MoneyAccountType {

	/** The drawer. {@code bankName} and {@code accountNumberLast4} are null on one. */
	CASH,

	BANK
}

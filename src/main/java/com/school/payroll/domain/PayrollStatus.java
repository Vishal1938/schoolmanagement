package com.school.payroll.domain;

/** Where one month's salary stands for one employee. */
public enum PayrollStatus {

	/** Computed and waiting to be paid. The only state a record may be deleted or re-run in. */
	PENDING,

	/** Paid. Advance recovery has been taken off the balance and nothing about it may change. */
	PAID
}

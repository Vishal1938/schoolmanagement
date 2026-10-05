package com.school.payroll.domain;

/** Whether an advance is still being recovered. */
public enum AdvanceStatus {

	/** Still owed; future payroll runs keep deducting the monthly installment. */
	ACTIVE,

	/** Fully recovered. Closed by the payment that recovered the last of it, never by hand. */
	CLOSED
}

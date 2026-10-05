package com.school.attendance.app;

/**
 * The days in a range that an employee was not paid for, as payroll (B13) sees them.
 *
 * <p>Only the two statuses that cost money are counted. PRESENT and LATE are a full day's work;
 * LEAVE is approved and therefore paid; holidays, non-working days and days nobody took the register
 * for are paid too, because an unmarked day is a gap in the record and not evidence of absence —
 * docking pay for it would make a forgotten register come out of somebody's salary.
 *
 * @param absentDays days marked ABSENT, each costing a full day
 * @param halfDays   days marked HALF_DAY, each costing half
 */
public record UnpaidDays(int absentDays, int halfDays) {

	/** Nothing to dock: used when there is no range to measure, or no register at all. */
	public static UnpaidDays none() {
		return new UnpaidDays(0, 0);
	}

	/** True when there is nothing to deduct, so the caller can skip the arithmetic entirely. */
	public boolean isNil() {
		return absentDays == 0 && halfDays == 0;
	}
}

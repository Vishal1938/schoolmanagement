package com.school.payroll.domain;

/**
 * How a deduction's {@code value} is to be read.
 *
 * <p>Two kinds cover what a school actually withholds: a flat professional tax, and PF or ESI as a
 * percentage of the basic. Both are expressed as a {@code long} so no money is ever a {@code double}
 * — see {@link #amountOf(long, long)} for the units.
 */
public enum DeductionType {

	/** {@code value} is the amount itself, in paise. */
	FIXED,

	/**
	 * {@code value} is a percentage of the basic in <strong>basis points</strong>: 1200 is 12%.
	 *
	 * <p>Hundredths of a percent rather than whole percent because PF and ESI rates are not whole
	 * numbers — ESI is 0.75% — and a percentage held as a {@code double} would quietly put a
	 * fractional paisa into somebody's salary.
	 */
	PERCENT_OF_BASIC;

	/** Basis points in a whole 100%. */
	private static final long FULL = 10_000L;

	/**
	 * What this deduction comes to, in paise, rounded half-up to the paisa.
	 *
	 * @param basic the structure's basic pay in paise, ignored by {@link #FIXED}
	 */
	public long amountOf(long basic, long value) {
		if (this == FIXED) {
			return Math.max(0L, value);
		}
		// Half-up, because rounding a deduction down every month is money the employee owes later.
		return Math.max(0L, (basic * value + FULL / 2) / FULL);
	}
}

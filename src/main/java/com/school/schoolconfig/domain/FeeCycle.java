package com.school.schoolconfig.domain;

/**
 * How often this institution collects the year's fees.
 *
 * <p>Purely a <em>default</em> for the screens: it tells the fee setup wizard which plan to offer and
 * the dashboard which period to head its figures with. It gates nothing. A deployment set to
 * {@link #SEMESTER} can still carry monthly installments, and one set to {@link #MONTHLY} can still
 * have a semester written by hand — the register and the ledger read whatever is actually on the
 * structure, because the installments are the truth and this is only what to suggest.
 *
 * <p><strong>Missing means {@link #MONTHLY}.</strong> Every configuration seeded before this field
 * existed is a school collecting month by month, so an existing deployment reads correctly without a
 * migration. Read it through {@code AcademicSettings.resolvedFeeCycle()} rather than off the field.
 */
public enum FeeCycle {

	/** Twelve slices of the year, one per month — what a school does, and the default. */
	MONTHLY,

	/** Two slices of the year, each half a session — what a college does. */
	SEMESTER
}

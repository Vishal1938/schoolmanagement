package com.school.fees.domain;

/**
 * What sort of charge an installment — and the invoices raised from it — is.
 *
 * <p>Purely a classification: the money, the allocation, the late fine and the receipt work
 * identically for all four. It exists so the fee register can lay the recurring slices of the year out
 * in one block and the one-off fees in another, and so a report can answer "how much of this period's
 * tuition is in" separately from "how much of the admission fee is in".
 *
 * <p>{@link #MONTHLY} and {@link #SEMESTER} are the two <em>recurring</em> kinds — the ones that carry
 * a period and get a register column in date order. Nothing else distinguishes them.
 *
 * <p><strong>Missing means {@link #MONTHLY}.</strong> Every installment and invoice written before
 * this field existed is a term or month of the year's fees, which is what MONTHLY means here, so a
 * deployment already running reads correctly without a migration. Read it through
 * {@code Installment.resolvedKind()} or {@code Invoice.resolvedKind()} rather than off the field.
 */
public enum InstallmentKind {

	/**
	 * A recurring slice of the year's fees — "Jul 2026", "Term 1", "Q3". One per month for a monthly
	 * plan, and what every installment created before this enum existed is taken to be.
	 */
	MONTHLY,

	/**
	 * Half a year of fees — "Semester 3 (Jul-Dec 2026)". What a college collects instead of twelve
	 * months, and the reason {@code periodStart} and {@code periodEnd} exist: a semester's period cannot
	 * be inferred from its due date the way a month's can.
	 */
	SEMESTER,

	/**
	 * A one-off charge the whole class pays — "Admission fee", "Laboratory deposit". Added to the
	 * structure, so {@code generate-invoices} raises it for every student on the roll.
	 */
	ONE_TIME,

	/**
	 * A charge for named students only — "Late library fine", "Bus fee for the Delhi trip". Not part of
	 * any structure: the invoice is created directly against the student and has no installment behind
	 * it, which is why a CUSTOM invoice is the one kind with no {@code structureId}.
	 */
	CUSTOM;

	/**
	 * A recurring slice of the year — MONTHLY or SEMESTER. These are the kinds that cover a period of
	 * the session and therefore the ones a register lays out in date order and a dashboard asks "which
	 * of these covers today" of.
	 */
	public boolean isRecurring() {
		return this == MONTHLY || this == SEMESTER;
	}
}

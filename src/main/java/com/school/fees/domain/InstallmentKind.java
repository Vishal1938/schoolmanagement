package com.school.fees.domain;

/**
 * What sort of charge an installment — and the invoices raised from it — is.
 *
 * <p>Purely a classification: the money, the allocation and the receipt work identically for all
 * three. It exists so the fee register can lay the months out in one block and the one-off fees in
 * another, and so a report can answer "how much of this month's tuition is in" separately from
 * "how much of the admission fee is in".
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
	 * A one-off charge the whole class pays — "Admission fee", "Laboratory deposit". Added to the
	 * structure, so {@code generate-invoices} raises it for every student on the roll.
	 */
	ONE_TIME,

	/**
	 * A charge for named students only — "Late library fine", "Bus fee for the Delhi trip". Not part of
	 * any structure: the invoice is created directly against the student and has no installment behind
	 * it, which is why a CUSTOM invoice is the one kind with no {@code structureId}.
	 */
	CUSTOM
}

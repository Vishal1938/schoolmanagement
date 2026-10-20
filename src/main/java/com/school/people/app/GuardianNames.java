package com.school.people.app;

/**
 * The three names a document about a student can be made out to besides the student's own.
 *
 * <p>A payment carries a free-text "paid by" name, because there are no parent logins in this system
 * and the receipt is the only record of who actually handed the money over. The office fills it from
 * what is on file, which means a module that takes money needs these names — and nothing else from
 * the family block, which is why this is a separate projection rather than three more fields on
 * {@link StudentContact}.
 *
 * <p>Any of the three may be null: not every student has both parents on record, and most have no
 * third guardian at all.
 */
public record GuardianNames(String fatherName, String motherName, String guardianName) {

	/**
	 * The best name to print for a payer of this relation, falling back through the guardian to
	 * {@code null} when the family details do not have it.
	 *
	 * @param father true for the father, false for the mother
	 */
	public String parentOrGuardian(boolean father) {
		String parent = father ? fatherName : motherName;
		return hasText(parent) ? parent : (hasText(guardianName) ? guardianName : null);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}

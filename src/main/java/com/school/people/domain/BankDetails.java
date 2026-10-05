package com.school.people.domain;

/**
 * Where this employee's salary is paid (B13).
 *
 * <p>The account number is the one field in the system stored encrypted. It is held twice and never
 * in the clear: {@code accountNumberEncrypted} is the AES-GCM value, and {@code accountNumberLast4}
 * is the only part kept readable, so a list can show "ending 4821" without decrypting a page of
 * rows — and so that a leak of the collection does not hand over anybody's account.
 *
 * @param accountName            the name the account is held in, which need not match the employee's
 * @param accountNumberEncrypted {@code v1:}-prefixed ciphertext from {@code FieldEncryptor}
 * @param accountNumberLast4     last four digits, in the clear, for display and reconciliation
 * @param ifsc                   branch code; not secret, and needed to make a payment
 * @param bankName               shown on the salary slip
 */
public record BankDetails(
		String accountName,
		String accountNumberEncrypted,
		String accountNumberLast4,
		String ifsc,
		String bankName) {
}

package com.school.people.domain;

/**
 * Who to contact about this student.
 *
 * <p>There are no parent logins in this system: a parent signs in as the student. These are contact
 * details and the names printed on documents, not accounts.
 *
 * <p>One phone number for the family rather than one per guardian — it is what the school actually
 * calls, and it is what {@code GET /students?q=} searches on.
 *
 * @param fatherName   may be absent; not every student has both parents on record
 * @param motherName   may be absent, for the same reason
 * @param guardianName the person responsible when it is neither parent
 * @param phone        the family's primary number; the one required contact detail
 * @param altPhone     a fallback number
 * @param email        optional. Not used for logging in: the student logs in with their uniqueId,
 *                     and several siblings may share this address
 * @param occupation   asked for on most admission forms
 */
public record Guardians(
		String fatherName,
		String motherName,
		String guardianName,
		String phone,
		String altPhone,
		String email,
		String occupation) {
}

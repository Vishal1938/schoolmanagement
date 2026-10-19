package com.school.people.app;

import com.school.auth.app.HashedPassword;
import com.school.auth.app.UserService;
import com.school.common.id.IdGenerator;
import com.school.common.id.IdType;
import com.school.common.security.TemporaryPasswordGenerator;

/**
 * Everything a person's creation needs that costs a round trip or a key derivation, computed before
 * the transaction that writes them opens.
 *
 * <p>Two things used to happen inside that transaction and no longer do. The {@code uniqueId} is a
 * {@code findAndModify} on the counters collection, and the password hash is BCrypt at strength 12 —
 * a few hundred milliseconds each. For a single admission neither mattered. For a three-hundred-row
 * import they added up to minutes, which is how the import came to exceed Atlas's sixty-second
 * transaction limit and fail with {@code NoSuchTransaction}.
 *
 * <p>Moving the ID out has one visible consequence: a rolled-back write no longer hands its number
 * back, so a failed admission leaves a gap in the STU series. That is what {@link IdGenerator}
 * already documents as the rule — counters only move forward — and a gap is cheaper than a slow
 * transaction.
 *
 * @param temporaryPassword the plain password, shown to the admin exactly once and never stored;
 *                          null when this person gets no login
 * @param passwordHash      what actually gets written; null for the same reason
 */
record PreparedLogin(String uniqueId, String temporaryPassword, HashedPassword passwordHash) {

	/**
	 * Draws an ID and, if this person is to have a login, a password and its hash.
	 *
	 * @param withLogin false for a student in a deployment with {@code studentLogin} off, or for a
	 *                  staff member hired without one. No password is generated and nothing is
	 *                  hashed — the expensive half of this is skipped entirely rather than computed
	 *                  and thrown away
	 */
	static PreparedLogin draw(IdType type, boolean withLogin, IdGenerator idGenerator,
			TemporaryPasswordGenerator temporaryPasswords, UserService users) {
		String uniqueId = idGenerator.next(type);
		if (!withLogin) {
			return new PreparedLogin(uniqueId, null, null);
		}
		String temporaryPassword = temporaryPasswords.generate();
		return new PreparedLogin(uniqueId, temporaryPassword, users.hash(temporaryPassword));
	}

	/** Whether a login is to be created under this ID. */
	boolean withLogin() {
		return passwordHash != null;
	}
}

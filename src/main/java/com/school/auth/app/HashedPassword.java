package com.school.auth.app;

/**
 * A password that has already been hashed, so the hashing can happen before a transaction opens.
 *
 * <p>BCrypt at strength 12 is deliberately slow — a few hundred milliseconds a password — which is
 * exactly what makes it the wrong thing to do inside a MongoDB transaction. Three hundred of them in
 * one transaction is well past Atlas's sixty-second ceiling, so a bulk import prepares all of its
 * hashes first and then writes each row in a transaction of its own.
 *
 * <p>There is still no way for a caller to choose a hash: the only way to get one of these is
 * {@link UserService#hash(String)}, which is the same encoder {@code UserService} would have used.
 * The type exists so that a hash cannot be mistaken for a plain password, in a signature or in a log
 * line — {@link #toString()} shows nothing.
 */
public record HashedPassword(String value) {

	/** Never the hash itself. This object ends up in service signatures; those get logged. */
	@Override
	public String toString() {
		return "HashedPassword[redacted]";
	}
}

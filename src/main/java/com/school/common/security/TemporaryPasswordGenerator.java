package com.school.common.security;

import java.security.SecureRandom;

import org.springframework.stereotype.Service;

/**
 * Issues the one-time passwords handed out with a new account (B5, B6) and on an admin reset.
 *
 * <p>The alphabet leaves out the characters that are read wrong off a printed slip — {@code O}/{@code 0},
 * {@code l}/{@code 1}/{@code I} — because every one of these is typed in from paper by somebody who
 * cannot ask what it said. Ten characters from the remaining 54 is about 58 bits, which is far more
 * than enough for a credential that must be changed on first login anyway.
 *
 * <p>The value is returned to the admin exactly once and is never stored in the clear, never logged
 * and never audited; only its BCrypt hash survives the request.
 */
@Service
public class TemporaryPasswordGenerator {

	/** No O, 0, l, 1 or I. */
	private static final char[] ALPHABET =
			"ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

	public static final int LENGTH = 10;

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		StringBuilder password = new StringBuilder(LENGTH);
		for (int i = 0; i < LENGTH; i++) {
			password.append(ALPHABET[random.nextInt(ALPHABET.length)]);
		}
		return password.toString();
	}
}

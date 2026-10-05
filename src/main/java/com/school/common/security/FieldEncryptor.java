package com.school.common.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.school.common.config.AppProperties;
import org.springframework.stereotype.Service;

/**
 * Field-level encryption for the few values that must be readable back but must not sit in the
 * database in the clear — today only employee bank account numbers (B6).
 *
 * <p>AES-256-GCM, so each value is authenticated as well as encrypted: a row edited directly in
 * Mongo fails to decrypt rather than returning a plausible wrong number. A fresh random 96-bit IV is
 * drawn per encryption and stored in front of the ciphertext, which is why encrypting the same
 * account number twice gives two different strings. That is deliberate — it also means this is not
 * searchable, so anything that needs to be looked up (the last four digits) is stored separately in
 * the clear.
 *
 * <p>Stored form is {@code v1:base64(iv || ciphertext || tag)}. The version prefix is there so a key
 * rotation or an algorithm change later can tell old values from new ones instead of guessing.
 *
 * <p>The key comes from {@code ENCRYPTION_KEY} and is never logged. A missing or malformed key is
 * reported on first use rather than at startup: a deployment that records no bank details — and
 * everything before B6 — has no reason to need one, and failing the whole application for it would
 * be worse than failing the one request that actually asked.
 */
@Service
public class FieldEncryptor {

	/** Prefix of the stored form; see the class comment. */
	private static final String VERSION = "v1:";

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final int KEY_BYTES = 32;

	private final SecureRandom random = new SecureRandom();

	/** Null when the key is unusable; {@link #key()} then explains why. */
	private final SecretKeySpec key;
	private final String keyProblem;

	public FieldEncryptor(AppProperties properties) {
		String configured = properties.encryption().key();
		SecretKeySpec parsed = null;
		String problem = null;
		if (configured == null || configured.isBlank()) {
			problem = "ENCRYPTION_KEY is not set";
		}
		else {
			try {
				byte[] bytes = Base64.getDecoder().decode(configured.trim());
				if (bytes.length != KEY_BYTES) {
					problem = "ENCRYPTION_KEY must decode to " + KEY_BYTES + " bytes for AES-256, got " + bytes.length;
				}
				else {
					parsed = new SecretKeySpec(bytes, "AES");
				}
			}
			catch (IllegalArgumentException ex) {
				problem = "ENCRYPTION_KEY is not valid Base64";
			}
		}
		this.key = parsed;
		this.keyProblem = problem;
	}

	/** @return the stored form, or null for null input, so an absent value stays absent */
	public String encrypt(String plaintext) {
		if (plaintext == null) {
			return null;
		}
		byte[] iv = new byte[IV_BYTES];
		random.nextBytes(iv);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			byte[] combined = new byte[iv.length + ciphertext.length];
			System.arraycopy(iv, 0, combined, 0, iv.length);
			System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
			return VERSION + Base64.getEncoder().encodeToString(combined);
		}
		catch (GeneralSecurityException ex) {
			// The message deliberately carries no part of the value or the key.
			throw new IllegalStateException("Field could not be encrypted: " + ex.getMessage(), ex);
		}
	}

	/** @return the plaintext, or null for null input */
	public String decrypt(String stored) {
		if (stored == null) {
			return null;
		}
		if (!stored.startsWith(VERSION)) {
			throw new IllegalStateException("Stored value is not in the " + VERSION + " encrypted form");
		}
		byte[] combined = Base64.getDecoder().decode(stored.substring(VERSION.length()));
		byte[] iv = new byte[IV_BYTES];
		System.arraycopy(combined, 0, iv, 0, IV_BYTES);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
			return new String(cipher.doFinal(combined, IV_BYTES, combined.length - IV_BYTES), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Field could not be decrypted. Either ENCRYPTION_KEY is not the key "
					+ "this value was written with, or the stored value has been tampered with.", ex);
		}
	}

	private SecretKeySpec key() {
		if (key == null) {
			throw new IllegalStateException(keyProblem
					+ ". Set it to a Base64 AES-256 key — generate one with: openssl rand -base64 32");
		}
		return key;
	}
}

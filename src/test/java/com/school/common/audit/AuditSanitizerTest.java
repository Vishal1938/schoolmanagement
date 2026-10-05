package com.school.common.audit;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule that matters most about the audit trail: it never contains a password, a hash or a token.
 * An audit entry is read by more people than the code that wrote it, and it is kept for years, so a
 * secret that lands here is a secret that stays there.
 */
class AuditSanitizerTest {

	private static final String PLAINTEXT = "Sup3rSecret!Password";
	private static final String BCRYPT = "$2a$12$GHuVsE9wkTEwR7ZvJgfF7uqqZj2Yp1kDe3iQxEwYGmH8tCkTpUmMa";
	private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.8f4a3c";

	private final AuditSanitizer sanitizer = new AuditSanitizer(new ObjectMapper());

	@Test
	void redactsEveryFieldThatLooksLikeCredentialMaterial() {
		Map<String, Object> sanitized = sanitizer.sanitize(Map.ofEntries(
				Map.entry("password", PLAINTEXT),
				Map.entry("newPassword", PLAINTEXT),
				Map.entry("currentPassword", PLAINTEXT),
				Map.entry("passwordHash", BCRYPT),
				Map.entry("passwd", PLAINTEXT),
				Map.entry("accessToken", JWT),
				Map.entry("refreshToken", "opaque-refresh-value"),
				Map.entry("tokenHash", "sha256-of-the-refresh-token"),
				Map.entry("jwtSecret", "signing-secret"),
				Map.entry("clientSecret", "razorpay-secret"),
				Map.entry("apiKey", "ai-provider-key"),
				Map.entry("storageAccessKey", "minioadmin"),
				Map.entry("privateKey", "-----BEGIN PRIVATE KEY-----"),
				Map.entry("authorization", "Bearer " + JWT),
				Map.entry("cookie", "refreshToken=opaque"),
				Map.entry("sessionId", "abc123"),
				Map.entry("otp", "482913"),
				Map.entry("credentials", "user:pass")));

		assertThat(sanitized.values()).containsOnly(AuditSanitizer.REDACTED);
	}

	@Test
	void keepsTheFieldsThatMakeTheTrailUseful() {
		Map<String, Object> sanitized = sanitizer.sanitize(Map.of(
				"uniqueId", "DEMO-STU-26-00001",
				"role", "STUDENT",
				"mustChangePassword", true,
				"enabled", false,
				"failedLoginAttempts", 3));

		// mustChangePassword contains "password" and is redacted; that is the deliberate trade-off.
		assertThat(sanitized).containsEntry("uniqueId", "DEMO-STU-26-00001")
				.containsEntry("role", "STUDENT")
				.containsEntry("enabled", false)
				.containsEntry("failedLoginAttempts", 3)
				.containsEntry("mustChangePassword", AuditSanitizer.REDACTED);
	}

	@Test
	void redactsInsideNestedObjectsAndLists() {
		Map<String, Object> sanitized = sanitizer.sanitize(Map.of(
				"user", Map.of("uniqueId", "DEMO-EMP-26-0001", "passwordHash", BCRYPT),
				"sessions", List.of(
						Map.of("device", "chrome", "refreshTokenHash", "hash-one"),
						Map.of("device", "android", "refreshTokenHash", "hash-two")),
				"tags", List.of("a", "b")));

		String asString = sanitized.toString();
		assertThat(asString).doesNotContain(BCRYPT).doesNotContain("hash-one").doesNotContain("hash-two");

		@SuppressWarnings("unchecked")
		Map<String, Object> user = (Map<String, Object>) sanitized.get("user");
		assertThat(user).containsEntry("uniqueId", "DEMO-EMP-26-0001")
				.containsEntry("passwordHash", AuditSanitizer.REDACTED);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> sessions = (List<Map<String, Object>>) sanitized.get("sessions");
		assertThat(sessions).allSatisfy(session ->
				assertThat(session).containsEntry("refreshTokenHash", AuditSanitizer.REDACTED));
		assertThat(sessions).first().extracting(session -> session.get("device")).isEqualTo("chrome");
		assertThat(sanitized.get("tags")).isEqualTo(List.of("a", "b"));
	}

	@Test
	void matchesFieldNamesWhateverTheirCase() {
		Map<String, Object> sanitized = sanitizer.sanitize(Map.of(
				"PASSWORD", PLAINTEXT, "Password_Hash", BCRYPT, "Access_Token", JWT));

		assertThat(sanitized.values()).containsOnly(AuditSanitizer.REDACTED);
	}

	@Test
	void sanitizesWholeEntitiesSoCallersCannotLeakByAccident() {
		// Shaped like the user document B2 introduces: handing the entity straight to the audit service
		// must not put its hash in the trail.
		record UserLike(String uniqueId, String passwordHash, String role, int failedLoginAttempts) {
		}

		Map<String, Object> sanitized = sanitizer.sanitize(
				new UserLike("DEMO-EMP-26-0001", BCRYPT, "ADMIN", 0));

		assertThat(sanitized).containsEntry("uniqueId", "DEMO-EMP-26-0001")
				.containsEntry("passwordHash", AuditSanitizer.REDACTED)
				.containsEntry("role", "ADMIN");
		assertThat(sanitized.toString()).doesNotContain(BCRYPT);
	}

	@Test
	void nullStaysNullSoNoPreviousStateIsDistinguishable() {
		assertThat(sanitizer.sanitize(null)).isNull();
	}
}

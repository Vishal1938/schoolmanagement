package com.school.common.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Typed view of the {@code app.*} configuration, which is fed entirely from the environment
 * variables listed in CLAUDE.md. Nothing school-specific is ever hardcoded: what is not an
 * environment variable lives in the {@code school_config} collection instead.
 *
 * @param apiBasePath prefix added to every controller in {@code com.school}; actuator and the
 *                    OpenAPI endpoints stay outside it
 * @param schoolCode  short code of the school this deployment serves
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
		@DefaultValue("/api/v1") @NotBlank String apiBasePath,
		@NotBlank String schoolCode,
		@DefaultValue("Asia/Kolkata") @NotBlank String timezone,
		@DefaultValue Features features,
		@DefaultValue Jwt jwt,
		@DefaultValue Storage storage,
		@DefaultValue Razorpay razorpay,
		@DefaultValue BootstrapAdmin bootstrapAdmin,
		@DefaultValue Encryption encryption) {

	/** Feature switches. AI stays off unless explicitly enabled (B18). */
	public record Features(@DefaultValue("false") boolean ai) {
	}

	/**
	 * Access-token signing and refresh-token lifetime (B2). The refresh cookie is scoped to
	 * {@code cookiePath} so it is never sent to non-auth endpoints.
	 */
	public record Jwt(
			String secret,
			@DefaultValue("15m") Duration accessTtl,
			@DefaultValue("7d") Duration refreshTtl,
			@DefaultValue("/api/v1/auth") String cookiePath) {
	}

	/** S3-compatible object storage: MinIO locally, anything S3-compatible in production. */
	public record Storage(
			String endpoint,
			String bucket,
			String accessKey,
			String secretKey,
			@DefaultValue("us-east-1") String region,
			@DefaultValue("public/") String publicPrefix,
			@DefaultValue("true") boolean pathStyleAccess) {
	}

	/** Payment gateway credentials (B12). The webhook secret verifies inbound callbacks. */
	public record Razorpay(String keyId, String keySecret, String webhookSecret) {
	}

	/** Used once, on first boot, to create the initial ADMIN account (B2). */
	public record BootstrapAdmin(String email, String password) {
	}

	/** Base64 AES key for field-level encryption of employee bank details (B6). */
	public record Encryption(String key) {
	}
}

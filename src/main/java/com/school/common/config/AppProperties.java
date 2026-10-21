package com.school.common.config;

import java.time.Duration;
import java.util.Locale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Typed view of the {@code app.*} configuration, which is fed entirely from the environment
 * variables listed in CLAUDE.md. Nothing school-specific is ever hardcoded: what is not an
 * environment variable lives in the {@code school_config} collection instead.
 *
 * @param apiBasePath   prefix added to every controller in {@code com.school}; actuator and the
 *                      OpenAPI endpoints stay outside it
 * @param schoolCode    short code of the school this deployment serves
 * @param publicBaseUrl the <strong>origin</strong> this API is reached on from outside — scheme, host
 *                      and port only, never a path. Optional, and only read by {@code OpenApiConfig}:
 *                      set it on any deployment behind a proxy, and the OpenAPI document advertises
 *                      it instead of guessing from the incoming request. Guessing is what makes
 *                      Swagger UI's Try-it-out fail with a CORS error on a host that answers both
 *                      http and https — see {@link #resolvedPublicBaseUrl()}
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
		@DefaultValue("/api/v1") @NotBlank String apiBasePath,
		@NotBlank String schoolCode,
		@DefaultValue("Asia/Kolkata") @NotBlank String timezone,
		String publicBaseUrl,
		@DefaultValue Features features,
		@DefaultValue Jwt jwt,
		@DefaultValue Storage storage,
		@DefaultValue Razorpay razorpay,
		@DefaultValue BootstrapAdmin bootstrapAdmin,
		@DefaultValue Encryption encryption,
		@DefaultValue Ai ai,
		@DefaultValue Seed seed,
		@DefaultValue DemoData demoData) {

	/**
	 * {@link #publicBaseUrl} without a trailing slash, or null when it is not configured.
	 *
	 * <p>Must be an origin and nothing more — {@code https://school.example}, not
	 * {@code https://school.example/api/v1}. Every path in the OpenAPI document already starts with
	 * {@link #apiBasePath}, so a base URL carrying it too would have generated clients calling
	 * {@code /api/v1/api/v1/...}. A trailing slash would do the same thing one character at a time,
	 * hence the trim here rather than a note in a README.
	 */
	public String resolvedPublicBaseUrl() {
		if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
			return null;
		}
		String trimmed = publicBaseUrl.trim();
		return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
	}

	/** Feature switches. AI stays off unless explicitly enabled (B18). */
	public record Features(@DefaultValue("false") boolean ai) {
	}

	/**
	 * Access-token signing and refresh-token lifetime (B2). The refresh cookie is scoped to
	 * {@code cookiePath} so it is never sent to non-auth endpoints.
	 *
	 * @param cookieSecure whether the refresh cookie carries {@code Secure}. True everywhere but the local
	 *                     profile, which is served over plain http
	 */
	public record Jwt(
			String secret,
			@DefaultValue("15m") Duration accessTtl,
			@DefaultValue("7d") Duration refreshTtl,
			@DefaultValue("/api/v1/auth") String cookiePath,
			@DefaultValue("true") boolean cookieSecure) {
	}

	/**
	 * S3-compatible object storage: MinIO locally, Cloudflare R2 (or any S3-compatible store) in
	 * production. Only the endpoint and credentials differ between the two.
	 *
	 * @param publicPrefix   key prefix whose objects are readable without a pre-signed URL. Locally
	 *                       docker compose grants this with an anonymous-download policy; on R2 it is
	 *                       the prefix exposed through the public bucket URL or custom domain.
	 * @param publicBaseUrl  URL public objects are served from, e.g. an R2 custom domain. When unset,
	 *                       {@code endpoint/bucket} is used, which is what MinIO serves locally.
	 * @param maxImageSize      per-file limit for image uploads, below the multipart transport limit
	 * @param maxAttachmentSize per-file limit for notice attachments (B10), likewise below it
	 * @param maxPaperSize      per-file limit for exam papers in the vault (B14), likewise below it
	 */
	public record Storage(
			String endpoint,
			String bucket,
			String accessKey,
			String secretKey,
			@DefaultValue("us-east-1") String region,
			@DefaultValue("public/") String publicPrefix,
			@DefaultValue("true") boolean pathStyleAccess,
			String publicBaseUrl,
			@DefaultValue("5MB") DataSize maxImageSize,
			@DefaultValue("10MB") DataSize maxAttachmentSize,
			@DefaultValue("20MB") DataSize maxPaperSize) {

		/** Base URL for public objects, without a trailing slash. */
		public String resolvedPublicBaseUrl() {
			String base = publicBaseUrl == null || publicBaseUrl.isBlank()
					? endpoint + "/" + bucket
					: publicBaseUrl;
			return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
		}
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

	/**
	 * The AI provider's credentials and model (B18). Read only when {@link Features#ai()} is true, so
	 * a school that has the feature off needs no key and starts without one.
	 *
	 * <p>There is no {@code provider} switch. OpenAI is the one starter on the classpath; moving to
	 * another provider means swapping that dependency and {@code AiModelConfig}, which a property
	 * could not do on its own.
	 *
	 * @param apiKey  from {@code AI_API_KEY}. Never logged, never returned by any endpoint
	 * @param model   from {@code AI_MODEL}; the provider's model id, not a friendly name
	 * @param timeout how long one provider call may take before it is a 502 {@code DEPENDENCY_FAILED}
	 */
	public record Ai(
			String apiKey,
			@DefaultValue("gpt-4o-mini") @NotBlank String model,
			@DefaultValue("30s") Duration timeout) {

		/** Whether a key has been configured at all. */
		public boolean hasApiKey() {
			return apiKey != null && !apiKey.isBlank();
		}
	}

	/**
	 * The demonstration data set (B20), from {@code DEMO_DATA_ENABLED}.
	 *
	 * <p>Off by default, and off is what a real deployment runs: with it off the endpoints under
	 * {@code /admin/demo-data} do not exist at all, so there is nothing for an administrator to
	 * invoke by accident on a school's live database.
	 */
	public record DemoData(@DefaultValue("false") boolean enabled) {
	}

	/**
	 * First-boot seeding of the {@code school_config} document (B1).
	 *
	 * @param enabled              set to {@code false} in tests, and anywhere the database must not be
	 *                             written to on startup
	 * @param profile              from {@code SEED_PROFILE}; picks the seed that ships inside the jar,
	 *                             {@code seed/<profile>-seed.json}
	 * @param schoolConfigLocation any Spring resource location. When set it wins over {@code profile};
	 *                             point it at {@code file:./seed/school-seed.json} to replace the seed
	 *                             for a deployment without rebuilding.
	 */
	public record Seed(
			@DefaultValue("true") boolean enabled,
			@DefaultValue("school") @NotNull Profile profile,
			String schoolConfigLocation) {

		/** The seed file to read: the explicit location if one is set, otherwise the profile's own. */
		public String resolvedLocation() {
			return schoolConfigLocation == null || schoolConfigLocation.isBlank()
					? "classpath:seed/" + profile.name().toLowerCase(Locale.ROOT) + "-seed.json"
					: schoolConfigLocation;
		}

		/** The bundled seeds. Each one names {@code seed/<lower-case name>-seed.json}. */
		public enum Profile {

			SCHOOL,

			COLLEGE
		}
	}
}

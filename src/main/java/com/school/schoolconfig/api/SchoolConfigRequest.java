package com.school.schoolconfig.api;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

import com.school.schoolconfig.domain.GradingMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The full configuration as written by {@code PUT /school/config}.
 *
 * <p>The seed file {@code seed/school-seed.json} is deliberately the same shape, so the seed and the
 * endpoint share one validated contract: anything the API would reject also fails at startup instead
 * of landing half-valid in the database.
 *
 * <p>{@code code} is absent on purpose — it comes from {@code SCHOOL_CODE} and cannot be changed
 * through the API.
 */
public record SchoolConfigRequest(
		@Valid @NotNull Identity identity,
		@Valid @NotNull Landing landing,
		@Valid @NotNull GradingScheme gradingScheme,
		@Valid @NotNull AcademicSettings academicSettings) {

	/** CSS hex colour, three or six digits. */
	private static final String HEX_COLOUR = "^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$";

	/** Deliberately loose: phone-number shapes differ by country and this code is school-agnostic. */
	private static final String PHONE = "^[+0-9][0-9 ()/-]{5,31}$";

	public record Identity(
			@NotBlank @Size(max = 120) String name,
			@Size(max = 200) String tagline,
			@Size(max = 512) String logoUrl,
			@Size(max = 512) String faviconUrl,
			@Valid @NotNull Theme theme) {
	}

	public record Theme(
			@NotBlank @Pattern(regexp = HEX_COLOUR, message = "must be a hex colour like #0B3D91") String primary,
			@NotBlank @Pattern(regexp = HEX_COLOUR, message = "must be a hex colour like #0B3D91") String secondary,
			@NotBlank @Pattern(regexp = HEX_COLOUR, message = "must be a hex colour like #0B3D91") String accent) {
	}

	/**
	 * Only {@code about}, {@code stats} and {@code contact} are required; the rest may be omitted or
	 * sent as null, and lists may be empty.
	 */
	public record Landing(
			@NotBlank @Size(max = 5000) String about,
			@Size(max = 2000) String vision,
			@Valid Principal principal,
			@Valid Academics academics,
			@Size(max = 12) List<@Valid Highlight> highlights,
			@Size(max = 40) List<@NotBlank @Size(max = 160) String> facilities,
			@Size(max = 60) List<@NotBlank @Size(max = 512) String> galleryImageUrls,
			@Valid @NotNull Stats stats,
			@Valid @NotNull ContactDetails contact,
			@Size(max = 1024) String mapEmbedUrl,
			@Valid SocialLinks socialLinks) {
	}

	public record Principal(
			@Size(max = 120) String name,
			@Size(max = 120) String designation,
			@Size(max = 512) String photoUrl,
			@Size(max = 5000) String message) {
	}

	public record Academics(
			@Size(max = 120) String board,
			@Size(max = 5000) String summary,
			@Size(max = 20) List<@Valid AcademicLevel> levels) {
	}

	/** A level that is listed at all has to be named; the rest is optional. */
	public record AcademicLevel(
			@NotBlank @Size(max = 80) String name,
			@Size(max = 40) String range,
			@Size(max = 1000) String description) {
	}

	/** A highlight that is listed at all has to have a title; the rest is optional. */
	public record Highlight(
			@NotBlank @Size(max = 120) String title,
			@Size(max = 500) String description,
			@Pattern(regexp = "^[A-Za-z0-9-]{1,60}$", message = "must be a Tabler icon name") String icon) {
	}

	public record Stats(
			@Min(0) int students,
			@Min(0) int teachers,
			@Min(0) int years,
			@Min(0) @Max(100) int passPercentage) {
	}

	public record ContactDetails(
			@NotBlank @Size(max = 200) String addressLine1,
			@Size(max = 200) String addressLine2,
			@NotBlank @Size(max = 100) String city,
			@Size(max = 100) String state,
			@Size(max = 16) String postalCode,
			@NotBlank @Pattern(regexp = PHONE, message = "must be a phone number") String phone,
			@Pattern(regexp = PHONE, message = "must be a phone number") String alternatePhone,
			@NotBlank @Email @Size(max = 200) String email,
			@Size(max = 512) String websiteUrl) {
	}

	public record SocialLinks(
			@Size(max = 512) String facebook,
			@Size(max = 512) String instagram,
			@Size(max = 512) String youtube,
			@Size(max = 512) String twitter,
			@Size(max = 512) String linkedin) {
	}

	/** Band overlaps and gaps are checked in the service, which Bean Validation cannot express. */
	public record GradingScheme(
			@NotNull GradingMode mode,
			@Valid @NotEmpty @Size(max = 20) List<GradeBand> bands) {
	}

	public record GradeBand(
			@NotBlank @Size(max = 8) String grade,
			@Min(0) @Max(100) int minPercentage,
			@Min(0) @Max(100) int maxPercentage,
			@Size(max = 60) String remark) {
	}

	public record AcademicSettings(
			@NotEmpty Set<DayOfWeek> workingDays,
			@Min(0) @Max(720) int attendanceEditWindowHours,
			@NotBlank @Pattern(regexp = "^[A-Z0-9-]{2,12}$",
					message = "must be 2-12 upper-case letters, digits or hyphens") String receiptPrefix,
			@Size(max = 500) String receiptFooter,
			@Size(max = 500) String salarySlipFooter,
			@Size(max = 500) String reportCardFooter) {
	}
}

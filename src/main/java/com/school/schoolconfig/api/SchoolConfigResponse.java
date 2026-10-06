package com.school.schoolconfig.api;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.school.schoolconfig.domain.GradingMode;

/**
 * The complete configuration, for {@code GET /school/config}. ADMIN only: it carries the receipt
 * prefix, the attendance edit window and the PDF footers, none of which are public.
 *
 * <p>Mirrors {@link SchoolConfigRequest} so an admin screen can load this, edit it and PUT it back,
 * with {@code code} and {@code updatedAt} added as read-only.
 */
public record SchoolConfigResponse(
		String code,
		Identity identity,
		Landing landing,
		GradingScheme gradingScheme,
		AcademicSettings academicSettings,
		Instant updatedAt) {

	public record Identity(String name, String tagline, String logoUrl, String faviconUrl, Theme theme) {
	}

	public record Theme(String primary, String secondary, String accent) {
	}

	public record Landing(
			String about,
			String vision,
			Principal principal,
			Academics academics,
			List<Highlight> highlights,
			List<String> facilities,
			List<String> galleryImageUrls,
			Stats stats,
			ContactDetails contact,
			String mapEmbedUrl,
			SocialLinks socialLinks) {
	}

	public record Principal(String name, String designation, String photoUrl, String message) {
	}

	public record Academics(String board, String summary, List<AcademicLevel> levels) {
	}

	public record AcademicLevel(String name, String range, String description) {
	}

	public record Highlight(String title, String description, String icon) {
	}

	public record Stats(int students, int teachers, int years, int passPercentage) {
	}

	public record ContactDetails(
			String addressLine1,
			String addressLine2,
			String city,
			String state,
			String postalCode,
			String phone,
			String alternatePhone,
			String email,
			String websiteUrl) {
	}

	public record SocialLinks(String facebook, String instagram, String youtube, String twitter, String linkedin) {
	}

	public record GradingScheme(GradingMode mode, List<GradeBand> bands) {
	}

	public record GradeBand(String grade, int minPercentage, int maxPercentage, String remark) {
	}

	public record AcademicSettings(
			Set<DayOfWeek> workingDays,
			int attendanceEditWindowHours,
			String receiptPrefix,
			String receiptFooter,
			String salarySlipFooter,
			String reportCardFooter) {
	}
}

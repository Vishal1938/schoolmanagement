package com.school.schoolconfig.api;

import java.util.List;
import java.util.Set;

/**
 * What an anonymous visitor may see: identity and landing-page content, nothing else.
 *
 * <p>This is a hand-written allowlist, not {@link SchoolConfigResponse} with fields blanked out. A
 * field added to the configuration later is therefore invisible here until someone adds it on
 * purpose, and {@link #FIELDS} lets a test assert the serialised key set exactly, so a leak fails the
 * build rather than reaching the internet.
 *
 * <p>Never add here: the receipt prefix, the attendance edit window, working days, the grading
 * scheme, PDF footer text, the school code or any audit field.
 *
 * <p>Only {@code name} is certain to be present. Everything else is optional in the configuration, so
 * a landing page must treat every field as possibly absent; the response omits nulls rather than
 * sending them.
 */
public record PublicSchoolResponse(
		String name,
		String tagline,
		String logoUrl,
		String faviconUrl,
		Theme theme,
		String about,
		String vision,
		Principal principal,
		Academics academics,
		List<Highlight> highlights,
		List<String> facilities,
		List<String> galleryImageUrls,
		Stats stats,
		Contact contact,
		String mapEmbedUrl,
		SocialLinks socialLinks) {

	/** The complete set of top-level keys this response may ever serialise. */
	public static final Set<String> FIELDS = Set.of(
			"name", "tagline", "logoUrl", "faviconUrl", "theme", "about", "vision", "principal",
			"academics", "highlights", "facilities", "galleryImageUrls", "stats", "contact",
			"mapEmbedUrl", "socialLinks");

	public record Theme(String primary, String secondary, String accent) {
	}

	/** Optional as a whole; a school with no principal section simply has no {@code principal} key. */
	public record Principal(String name, String designation, String photoUrl, String message) {
	}

	public record Academics(String board, String summary, List<AcademicLevel> levels) {
	}

	public record AcademicLevel(String name, String range, String description) {
	}

	/** {@code icon} is a Tabler icon name the frontend resolves; the backend never interprets it. */
	public record Highlight(String title, String description, String icon) {
	}

	public record Stats(int students, int teachers, int years, int passPercentage) {
	}

	/** The office address as published on the site. No internal contact is stored here. */
	public record Contact(
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
}

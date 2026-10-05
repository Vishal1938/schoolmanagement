package com.school.schoolconfig.domain;

/** Profile URLs for the landing-page footer. Any of them may be null when the school has none. */
public record SocialLinks(
		String facebook,
		String instagram,
		String youtube,
		String twitter,
		String linkedin) {
}

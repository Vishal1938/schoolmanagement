package com.school.schoolconfig.domain;

import java.util.List;

/**
 * Everything the public landing page renders. Held in the database rather than in the frontend so a
 * new deployment needs no code change, only a seed file.
 *
 * <p>Only {@code about}, {@code stats} and {@code contact} are required. Everything else is optional:
 * a school that has not written a vision statement, named its principal or listed its academic stages
 * still gets a working landing page, and the public endpoint omits what is missing rather than
 * failing.
 */
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

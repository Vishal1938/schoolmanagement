package com.school.schoolconfig.domain;

/**
 * Who the school is, as shown in the UI, on PDFs and in the browser tab. Every field here is
 * public-safe.
 */
public record Identity(
		String name,
		String tagline,
		String logoUrl,
		String faviconUrl,
		Theme theme) {
}

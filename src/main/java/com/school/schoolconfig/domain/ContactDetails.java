package com.school.schoolconfig.domain;

/**
 * Postal address and the ways to reach the office. Deliberately free-form strings rather than a
 * country-specific shape, so the same code serves a school anywhere.
 */
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

package com.school.people.domain;

/**
 * A postal address. Structured rather than one free-text blob because transfer certificates and the
 * Excel import in B5 part 2 both need the parts separately.
 */
public record Address(
		String line1,
		String line2,
		String city,
		String state,
		String postalCode) {
}

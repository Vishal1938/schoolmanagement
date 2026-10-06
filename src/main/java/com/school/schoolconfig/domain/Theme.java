package com.school.schoolconfig.domain;

/**
 * Brand colours as CSS hex strings. The frontend feeds these into CSS custom properties, which is
 * why no colour is ever hardcoded in either repo.
 */
public record Theme(String primary, String secondary, String accent) {
}

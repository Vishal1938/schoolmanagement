package com.school.people.api;

/**
 * The answer to {@code POST /students/{uniqueId}/reset-password}. Shown once, for the same reasons
 * as {@link StudentCreatedResponse#temporaryPassword()}; calling the endpoint again issues another
 * one rather than repeating this.
 */
public record TemporaryPasswordResponse(String uniqueId, String temporaryPassword) {
}

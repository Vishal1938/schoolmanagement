package com.school.people.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /students/promote/preview} and {@code POST /students/promote}.
 *
 * <p>One shape for both, so the client can send exactly what it previewed. The preview ignores
 * {@code excludeUniqueIds} — it is the "who is in each class" question, asked before anybody has
 * decided who stays behind.
 *
 * <p>The session being promoted <em>out of</em> is not in the body: it is always the active one.
 * Letting a client name it would allow a promotion to be run against a year that closed two years
 * ago, which is the one mistake in this operation nobody could undo by hand.
 *
 * @param toSessionId  the session to promote into. Must exist and must not be the active one;
 *                     promotion does not change which session is active, so the admin activates the
 *                     new one separately, once they are satisfied with the result
 * @param mappings     one entry per class being promoted. A class not listed is left alone
 */
public record PromotionRequest(
		@NotBlank String toSessionId,
		@Valid @NotEmpty @Size(max = 100) List<Mapping> mappings,
		@Size(max = 5000) List<@NotBlank String> excludeUniqueIds) {

	/**
	 * Where one class's students go.
	 *
	 * @param toClassId the class they move up into, or <strong>null for the final class</strong>,
	 *                  whose students have finished school and become {@code ALUMNI} instead
	 */
	public record Mapping(@NotBlank String fromClassId, String toClassId) {
	}

	/** Never null, so callers can iterate it without checking. */
	public List<String> excludeUniqueIds() {
		return excludeUniqueIds == null ? List.of() : excludeUniqueIds;
	}
}

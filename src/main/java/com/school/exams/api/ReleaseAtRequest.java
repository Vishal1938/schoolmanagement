package com.school.exams.api;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/**
 * Moves a paper's release time.
 *
 * <p>Not constrained to the future here: an admin postponing an exam moves it later, while an admin
 * whose exam has been brought forward sets it to now, and both are legitimate. The time a paper is
 * <em>first</em> given, on upload, must be in the future.
 */
public record ReleaseAtRequest(@NotNull Instant releaseAt) {
}

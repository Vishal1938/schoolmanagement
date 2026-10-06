package com.school.exams.api;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /exams} and {@code PUT /exams/{id}}.
 *
 * <p>{@code sessionId} is absent: an exam always belongs to the active session, which is not the
 * client's to choose. {@code status} is absent too — it moves through its own endpoint, so editing
 * a date cannot publish results.
 */
public record ExamRequest(
		@NotBlank @Size(max = 80) String name,
		@NotEmpty @Size(max = 30) List<@NotBlank String> classIds,
		@Valid @NotEmpty @Size(max = 40) List<Paper> schedule) {

	/** {@code passMarks} must not exceed {@code maxMarks}; that pair is checked in the service. */
	public record Paper(
			@NotBlank String subjectId,
			@NotNull LocalDate date,
			@Min(1) int maxMarks,
			@Min(0) int passMarks) {
	}
}

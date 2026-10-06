package com.school.exams.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /exams/{examId}/marks}: one subject's column for one class-section.
 *
 * <p>An upsert per row rather than a replace: a student left out of {@code entries} keeps whatever
 * mark they had. That is the opposite of the attendance register, and deliberately so — a teacher
 * entering the second half of a class should not wipe the first half.
 */
public record MarksEntryRequest(@Valid @NotNull @Size(max = 200) List<Entry> entries) {

	/**
	 * @param marksObtained null for an unmarked or absent paper. Must be between 0 and the paper's
	 *                      {@code maxMarks}; anything else fails the whole request
	 * @param absent        when true, {@code marksObtained} is stored as null whatever was sent
	 */
	public record Entry(
			@NotBlank String studentUniqueId,
			@Min(0) Integer marksObtained,
			boolean absent,
			@Size(max = 200) String remarks) {
	}
}

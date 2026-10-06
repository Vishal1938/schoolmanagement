package com.school.academics.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /classes/{id}/assignments}: the complete staffing of a class.
 *
 * <p>This is a replace, not a merge. A section left out of the list ends up with nobody assigned, so
 * the client sends the whole picture it wants rather than a diff — which is also what makes the
 * before-and-after in the audit trail readable.
 */
public record ClassAssignmentsRequest(
		@Valid @NotNull @Size(max = 26) List<SectionAssignment> assignments) {

	/**
	 * @param classTeacher  the section's class teacher by {@code uniqueId}, or null for none
	 */
	public record SectionAssignment(
			@NotBlank @Size(max = 8) String section,
			@Size(max = 40) String classTeacher,
			@Valid @Size(max = 40) List<SubjectTeacher> subjectTeachers) {
	}

	public record SubjectTeacher(
			@NotBlank String subjectId,
			@NotBlank @Size(max = 40) String teacher) {
	}
}

package com.school.academics.api;

import java.util.List;

import com.school.academics.domain.SchoolClass;

/**
 * A class as the API returns it, including its sections, subjects and staffing.
 *
 * <p>Teachers are {@code uniqueId}s. The picker at {@code GET /users/teachers} supplies the names, so
 * the frontend holds one list rather than the same name repeated across every class.
 */
public record ClassResponse(
		String id,
		String name,
		int order,
		List<String> sections,
		List<String> subjectIds,
		List<SectionAssignment> assignments) {

	public record SectionAssignment(String section, String classTeacher, List<SubjectTeacher> subjectTeachers) {
	}

	public record SubjectTeacher(String subjectId, String teacher) {
	}

	public static ClassResponse from(SchoolClass schoolClass) {
		List<SectionAssignment> assignments = schoolClass.getAssignments() == null
				? List.of()
				: schoolClass.getAssignments().stream().map(ClassResponse::toSectionAssignment).toList();
		return new ClassResponse(schoolClass.getId(), schoolClass.getName(), schoolClass.getOrder(),
				schoolClass.getSections() == null ? List.of() : schoolClass.getSections(),
				schoolClass.getSubjectIds() == null ? List.of() : schoolClass.getSubjectIds(),
				assignments);
	}

	private static SectionAssignment toSectionAssignment(com.school.academics.domain.SectionAssignment assignment) {
		List<SubjectTeacher> subjectTeachers = assignment.subjectTeachers() == null
				? List.of()
				: assignment.subjectTeachers().stream()
						.map(st -> new SubjectTeacher(st.subjectId(), st.teacherUniqueId()))
						.toList();
		return new SectionAssignment(assignment.section(), assignment.classTeacherUniqueId(), subjectTeachers);
	}
}

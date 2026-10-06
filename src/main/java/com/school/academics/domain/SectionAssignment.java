package com.school.academics.domain;

import java.util.List;

/**
 * The teaching staff of one section of one class.
 *
 * @param section               the section letter, e.g. {@code A}; always one the class actually has
 * @param classTeacherUniqueId  the section's class teacher, or null while nobody is assigned
 * @param subjectTeachers       at most one entry per subject on the class's subject list
 */
public record SectionAssignment(String section, String classTeacherUniqueId,
		List<SubjectTeacher> subjectTeachers) {
}

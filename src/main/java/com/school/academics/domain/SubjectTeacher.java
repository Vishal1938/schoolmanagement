package com.school.academics.domain;

/**
 * Who teaches one subject in one section.
 *
 * @param subjectId        a {@link Subject} that is on the class's subject list
 * @param teacherUniqueId  the teacher's login {@code uniqueId}. A uniqueId rather than an employee id
 *                         because the employees module does not exist yet (B6); it is stable either
 *                         way, since ids are never reissued.
 */
public record SubjectTeacher(String subjectId, String teacherUniqueId) {
}

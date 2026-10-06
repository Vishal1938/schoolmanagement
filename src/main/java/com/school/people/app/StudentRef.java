package com.school.people.app;

/**
 * A student as other modules see them: enough to name one, put them in roll order, and know which
 * class-section they sit in.
 *
 * <p>Deliberately not the {@code Student} document. Attendance needs a register of who is in 5-B and
 * exams need to rank within it — neither needs guardian phone numbers, and a module that cannot name
 * the document cannot leak it.
 *
 * @param classId the current enrollment's class, null if the student has no enrollment
 * @param section the current enrollment's section, upper-case
 * @param rollNo  the roll number in the current enrollment, which is the order a register is read in
 */
public record StudentRef(String uniqueId, String name, String classId, String section, int rollNo) {
}

package com.school.people.app;

/**
 * An employee as other modules see them: enough to name one, group teachers apart from staff, and
 * print who they are at the top of a document.
 *
 * <p>{@code employeeType} is a plain string rather than the enum so that the documents package does
 * not have to be exposed alongside this one; the values are {@code TEACHER} and {@code STAFF}.
 *
 * @param designation the staff job title, null for a teacher — teachers have none, and a salary slip
 *                    falls back to the type for them
 */
public record EmployeeRef(String uniqueId, String name, String employeeType, String designation) {
}

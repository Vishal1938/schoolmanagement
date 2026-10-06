package com.school.exams.app;

/**
 * A rendered report card: the bytes, and what to call the file when a browser saves it.
 *
 * @param fileName suggested download name, already safe for a {@code Content-Disposition} header
 */
public record ReportCard(String fileName, byte[] pdf) {
}

package com.school.payroll.app;

/**
 * A rendered salary slip: the bytes, and what to call the file when a browser saves it.
 *
 * @param fileName suggested download name, already safe for a {@code Content-Disposition} header
 */
public record SalarySlip(String fileName, byte[] pdf) {
}

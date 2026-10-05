package com.school.payment.app;

/**
 * A rendered receipt: the bytes, and what to call the file when a browser saves it.
 *
 * @param fileName suggested download name, already safe for a {@code Content-Disposition} header
 */
public record Receipt(String fileName, byte[] pdf) {
}

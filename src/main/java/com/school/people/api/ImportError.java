package com.school.people.api;

/**
 * One thing wrong with one cell of an imported spreadsheet.
 *
 * @param row     the spreadsheet row number as the admin sees it in Excel, so the header is row 1 and
 *                the first record is row 2. Not an index into anything
 * @param column  the column header exactly as the template writes it, asterisk and all
 * @param message what is wrong with it, in words an administrator can act on
 */
public record ImportError(int row, String column, String message) {
}

package com.school.people.api;

import java.util.List;

/**
 * The answer to {@code POST /students/import} and {@code POST /employees/import}.
 *
 * <p>An import is all or nothing. Every row is validated before anything is written, so this is
 * either a rejection listing every bad cell in the file — with <strong>nothing</strong> created — or
 * a success with the count. There is no partial outcome to report, which is why there is no
 * {@code failed} array beside {@code created}.
 *
 * <p>Both shapes come back as 200: a spreadsheet with mistakes in it is a normal answer to this
 * endpoint, and the per-cell detail does not fit the {@code ProblemDetail} error body. A malformed
 * file — not a {@code .xlsx} at all, or over the size limit — is a 400 in the usual shape.
 *
 * <p>{@code null} fields are omitted from the JSON, so a rejection carries only {@code valid} and
 * {@code errors}, and a success only {@code valid}, {@code created} and {@code credentialsFileId}.
 *
 * @param credentialsFileId id for {@code GET /imports/credentials/{id}}, which downloads the one and
 *                          only copy of the temporary passwords this import issued. Null when the
 *                          import created no logins at all
 */
public record ImportResult(boolean valid, Integer created, String credentialsFileId, List<ImportError> errors) {

	/** Nothing was written; these are every bad cell in the file, in the order they were met. */
	public static ImportResult rejected(List<ImportError> errors) {
		return new ImportResult(false, null, null, List.copyOf(errors));
	}

	public static ImportResult created(int created, String credentialsFileId) {
		return new ImportResult(true, created, credentialsFileId, null);
	}
}

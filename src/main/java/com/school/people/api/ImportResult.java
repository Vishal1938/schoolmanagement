package com.school.people.api;

import java.util.List;

/**
 * The answer to {@code POST /students/import} and {@code POST /employees/import}.
 *
 * <p><strong>Validation is all or nothing.</strong> Every row is checked before anything is written,
 * so a file with a single bad cell creates nothing and comes back with every bad cell in it listed at
 * once. That part has not changed and is the shape most imports end in.
 *
 * <p><strong>Writing is not.</strong> The rows that passed validation are written one short
 * transaction at a time rather than in one long one — a three-hundred-row import cannot hold a single
 * MongoDB transaction open for the time it takes without exceeding the sixty-second limit Atlas
 * enforces. So there is a third outcome: the file validated, {@code created} rows landed, and then
 * row {@code failedAtRow} could not be written. The import stops there rather than carrying on past
 * it, because whatever stopped row 180 will almost certainly stop row 181, and a long list of
 * identical failures is not more useful than the first one.
 *
 * <p>A partial outcome is still {@code valid: true}: the spreadsheet was fine, and there is nothing in
 * it for the admin to correct. What they do instead is upload the same file again — the rows already
 * written are recognised and skipped, and the import picks up where it stopped. {@code skipped}
 * counts those on that second run.
 *
 * <p>All three shapes come back as 200: a spreadsheet with mistakes in it is a normal answer to this
 * endpoint, and the per-cell detail does not fit the {@code ProblemDetail} error body. A malformed
 * file — not a {@code .xlsx} at all, or over the size limit — is a 400 in the usual shape.
 *
 * <p>{@code null} fields are omitted from the JSON, so a rejection carries only {@code valid} and
 * {@code errors}, and a clean import only {@code valid}, {@code created} and
 * {@code credentialsFileId}.
 *
 * @param credentialsFileId id for {@code GET /imports/credentials/{id}}, which downloads the one and
 *                          only copy of the temporary passwords this import issued. Null when the
 *                          import created no logins at all. Present on a partial import too, for the
 *                          rows that did land — their passwords exist nowhere else
 * @param skipped           rows passed over because they were already imported by an earlier run of
 *                          the same file. Null when nothing was skipped
 * @param failedAtRow       the spreadsheet row, as Excel numbers it, that could not be written. Null
 *                          unless the import stopped partway
 * @param error             why that row failed, in one line. Null for the same reason
 */
public record ImportResult(boolean valid, Integer created, Integer skipped, String credentialsFileId,
		Integer failedAtRow, String error, List<ImportError> errors) {

	/** Nothing was written; these are every bad cell in the file, in the order they were met. */
	public static ImportResult rejected(List<ImportError> errors) {
		return new ImportResult(false, null, null, null, null, null, List.copyOf(errors));
	}

	/** Every row the file still had to contribute was written. */
	public static ImportResult created(int created, int skipped, String credentialsFileId) {
		return new ImportResult(true, created, zeroAsNull(skipped), credentialsFileId, null, null, null);
	}

	/** The file validated and then stopped at a row. {@code created} rows are on file and stay there. */
	public static ImportResult partial(int created, int skipped, String credentialsFileId, int failedAtRow,
			String error) {
		return new ImportResult(true, created, zeroAsNull(skipped), credentialsFileId, failedAtRow, error, null);
	}

	/** Keeps {@code "skipped": 0} out of the response for the ordinary first-time import. */
	private static Integer zeroAsNull(int count) {
		return count == 0 ? null : count;
	}
}

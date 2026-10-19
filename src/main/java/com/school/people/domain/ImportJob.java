package com.school.people.domain;

import java.time.Instant;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The mark an import leaves behind when it stops halfway, so that uploading the same file again
 * finishes it instead of starting it over.
 *
 * <p>This exists because the import is no longer one transaction. Rows are validated together and
 * then written one short transaction at a time, which is the only way a three-hundred-row file can be
 * imported at all against Atlas's sixty-second transaction limit — but it means a row that fails on
 * the write leaves the rows before it committed. An administrator's instinct at that point is to fix
 * nothing and upload the file again, and without this document that second upload would be rejected
 * wholesale: every row already written is a duplicate admission number.
 *
 * <p>So the stopping point is recorded against the <strong>file's own content hash</strong>. Upload
 * the same bytes again and the import recognises them, skips the rows it already wrote, and carries
 * on from the one that failed. Edit the file and the hash changes, which is the right outcome — a
 * different file is a different import, and the duplicate checks should have their say.
 *
 * <p>The job is marked {@link ImportJobStatus#COMPLETED} once a run gets to the end of the file. A
 * third upload of a finished file is then an ordinary import again, and reports its duplicates.
 *
 * @param key         {@code kind:sha256} of the uploaded bytes. The hash is of the file, never of
 *                    anything in it, so this document carries no personal data at all
 * @param failedAtRow the spreadsheet row, as Excel numbers it, that could not be written. Rows before
 *                    it are known to have landed
 * @param error       what went wrong, kept so a second failure at the same row can be compared with
 *                    the first rather than guessed at
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(ImportJob.COLLECTION)
public class ImportJob {

	public static final String COLLECTION = "import_jobs";

	@Id
	private String id;

	/** Unique, so two admins uploading the same file at once cannot each leave their own mark. */
	@Indexed(name = "import_jobs_key_idx", unique = true)
	private String key;

	private ImportKind kind;

	private ImportJobStatus status;

	/** Rows written across every run of this file, which is what the second run reports as skipped. */
	private int created;

	private Integer failedAtRow;

	private String error;

	private Instant startedAt;

	private Instant updatedAt;
}

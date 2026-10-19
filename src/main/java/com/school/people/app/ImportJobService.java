package com.school.people.app;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import com.school.common.exceptions.AppException;
import com.school.common.exceptions.ErrorType;
import com.school.people.domain.ImportJob;
import com.school.people.domain.ImportJobStatus;
import com.school.people.domain.ImportKind;
import com.school.people.infra.ImportJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Remembers where an import stopped, so re-uploading the same file resumes it.
 *
 * <p>The identity of an import is the SHA-256 of the uploaded bytes. Nothing about who uploaded it or
 * what was in it: the same spreadsheet is the same import, whoever sends it and however many times.
 *
 * <p>Deliberately not transactional and deliberately never fatal. The mark is an aid to the admin's
 * next upload, not part of the import's correctness — the rows themselves are what has or has not
 * been written. If the mark cannot be saved, the import still reports exactly which row failed, and
 * {@link #failed} says so in the log rather than replacing the real failure with its own.
 */
@Service
public class ImportJobService {

	private static final Logger log = LoggerFactory.getLogger(ImportJobService.class);

	private final ImportJobRepository jobs;
	private final Clock clock;

	public ImportJobService(ImportJobRepository jobs, Clock clock) {
		this.jobs = jobs;
		this.clock = clock;
	}

	/** The key this file imports under, and what its stopping point would be recorded against. */
	public static String keyFor(ImportKind kind, byte[] bytes) {
		return kind.name() + ":" + sha256(bytes);
	}

	/**
	 * The unfinished run of this exact file, if there is one.
	 *
	 * <p>Present means the caller is resuming: rows up to {@code failedAtRow}, and any row whose
	 * person is already on file, are to be skipped rather than rejected as duplicates. A
	 * {@link ImportJobStatus#COMPLETED} job answers empty — a third upload of a file that finished is
	 * an ordinary import, and should report its duplicates the way any other re-upload would.
	 */
	public Optional<ImportJob> resumable(String key) {
		return jobs.findByKey(key).filter(job -> job.getStatus() == ImportJobStatus.PARTIAL);
	}

	/**
	 * Records that this file stopped at a row, having written {@code created} of them.
	 *
	 * @param alreadyCreated what earlier runs of the same file had written, so {@code created} on the
	 *                       job stays a total across runs rather than resetting each time
	 */
	public void failed(String key, ImportKind kind, int created, int alreadyCreated, int failedAtRow, String error) {
		save(key, kind, ImportJobStatus.PARTIAL, alreadyCreated + created, failedAtRow, error);
	}

	/**
	 * Marks this file finished. Only writes anything when there was a mark to clear: a clean import
	 * first time round leaves no job behind, because there is nothing about it anybody has to resume.
	 */
	public void completed(String key, ImportKind kind, int created, int alreadyCreated) {
		if (jobs.findByKey(key).isEmpty()) {
			return;
		}
		save(key, kind, ImportJobStatus.COMPLETED, alreadyCreated + created, null, null);
	}

	// --- internals --------------------------------------------------------------------------------

	private void save(String key, ImportKind kind, ImportJobStatus status, int created, Integer failedAtRow,
			String error) {
		try {
			Instant now = Instant.now(clock);
			ImportJob existing = jobs.findByKey(key).orElse(null);
			ImportJob.ImportJobBuilder builder = existing != null
					? existing.toBuilder()
					: ImportJob.builder().key(key).kind(kind).startedAt(now);
			jobs.save(builder
					.status(status)
					.created(created)
					.failedAtRow(failedAtRow)
					// Truncated: this is a diagnostic line, and a driver exception message can run long.
					.error(error == null ? null : error.substring(0, Math.min(error.length(), 500)))
					.updatedAt(now)
					.build());
		}
		catch (RuntimeException ex) {
			// Never let bookkeeping mask the outcome the caller is about to report.
			log.warn("Could not record the {} import job as {}: {}", kind, status, ex.getMessage());
		}
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new AppException(ErrorType.INTERNAL_ERROR, "SHA-256 is not available in this JVM", ex);
		}
	}
}

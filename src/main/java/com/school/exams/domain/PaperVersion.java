package com.school.exams.domain;

import java.time.Instant;

/**
 * One uploaded version of an exam paper.
 *
 * <p>Versions are append-only: a corrected paper is a new version and the old one stays, because
 * "which file did the printer actually get" is a question that gets asked after the exam, when the
 * only honest answer is the bytes that were there at the time.
 *
 * @param storageKey where the PDF sits in the object store. <strong>Never leaves the server</strong> —
 *                   no response carries it; downloads are pre-signed URLs issued per request
 * @param fileName   the uploader's file name, sanitized, used for the download
 * @param uploadedBy {@code uniqueId} of whoever uploaded it
 */
public record PaperVersion(
		int versionNo,
		String storageKey,
		String fileName,
		long sizeBytes,
		String uploadedBy,
		Instant uploadedAt) {
}

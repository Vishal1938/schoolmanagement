package com.school.exams.api;

import java.time.Instant;

/**
 * A paper in the vault, as the list endpoint returns it: <strong>metadata only</strong>.
 *
 * <p>There is deliberately no storage key and no URL here. The only way to the bytes is
 * {@code GET /exam-papers/{id}/download}, which checks {@link #releaseAt} against who is asking and
 * writes an audit entry. A key in this response would be a way around both.
 *
 * @param locked        true while {@link #releaseAt} is still in the future. Other teachers get 423
 *                      from the download endpoint; the uploader and an admin do not
 * @param latestVersion what a download with no {@code version} parameter will hand over
 */
public record ExamPaperResponse(
		String id,
		String title,
		String examId,
		String examName,
		String classId,
		String className,
		String subjectId,
		String subjectName,
		Instant releaseAt,
		boolean locked,
		Version latestVersion,
		int versionCount,
		String createdBy,
		String createdByName,
		Instant createdAt) {

	/** One version, without its storage key. */
	public record Version(
			int versionNo,
			String fileName,
			long sizeBytes,
			String uploadedBy,
			String uploadedByName,
			Instant uploadedAt) {
	}
}

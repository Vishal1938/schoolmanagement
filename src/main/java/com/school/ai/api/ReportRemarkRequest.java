package com.school.ai.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Who to write a remark about, for {@code POST /ai/report-remarks}.
 *
 * <p>The exam must be PUBLISHED and must be one the student sat. The remark is not saved anywhere:
 * it comes back as text for a teacher to read, edit and put on the report card themselves.
 */
public record ReportRemarkRequest(
		@NotBlank @Size(max = 64) String examId,
		@NotBlank @Size(max = 64) String studentUniqueId) {
}

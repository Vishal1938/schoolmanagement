package com.school.exams.api;

import java.time.Instant;
import java.util.List;

import com.school.common.audit.AuditLogResponse;
import com.school.common.pagination.PageResponse;
import com.school.common.security.HasPermission;
import com.school.exams.app.ExamPaperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The exam paper vault.
 *
 * <p>{@code @PreAuthorize} here only answers "may this caller use this endpoint at all", which keeps
 * students and staff out entirely — they do not hold {@code EXAM_PAPER_READ}, so they get 403. Who
 * may read a particular paper <em>when</em>, and who may replace or release it, is decided in
 * {@link ExamPaperService}.
 */
@RestController
@RequestMapping("/exam-papers")
@Tag(name = "Exam papers", description = "The question paper vault: private storage with a release time")
public class ExamPaperController {

	private final ExamPaperService papers;

	public ExamPaperController(ExamPaperService papers) {
		this.papers = papers;
	}

	@GetMapping
	@PreAuthorize(HasPermission.EXAM_PAPER_READ)
	@Operation(summary = "Papers in the vault",
			description = "ADMIN and teachers. Metadata only — no storage keys and no URLs; use the download "
					+ "endpoint for those. `locked` is true while the paper is still before its releaseAt.")
	public List<ExamPaperResponse> list(
			@RequestParam(required = false) String examId,
			@RequestParam(required = false) String classId) {
		return papers.list(examId, classId);
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.EXAM_PAPER_UPLOAD)
	@Operation(summary = "Upload a paper",
			description = "ADMIN and teachers. `multipart/form-data` with `file` plus examId, classId, "
					+ "subjectId, releaseAt and an optional title. PDF only, identified by the file's own "
					+ "leading bytes rather than its name or Content-Type, up to 20 MB. The class must sit the "
					+ "exam and the subject must be in its schedule. One paper per (exam, class, subject): a "
					+ "second upload adds a version and keeps the old one, and only the teacher who uploaded "
					+ "it or the office may do that. releaseAt is required the first time and must be in the "
					+ "future; on a later version it is optional and the paper keeps its release time.")
	public ExamPaperResponse upload(
			@RequestParam("file") MultipartFile file,
			@RequestParam String examId,
			@RequestParam String classId,
			@RequestParam String subjectId,
			@RequestParam(required = false) Instant releaseAt,
			@RequestParam(required = false) String title) {
		return papers.upload(file, examId, classId, subjectId, releaseAt, title);
	}

	@PutMapping("/{id}/release-at")
	@PreAuthorize(HasPermission.EXAM_PAPER_UPLOAD)
	@Operation(summary = "Move a paper's release time",
			description = "The office whenever; the teacher who uploaded it only while it is still locked — "
					+ "once a paper is out, pulling it back would not unsee it. 422 otherwise.")
	public ExamPaperResponse changeReleaseAt(@PathVariable String id, @Valid @RequestBody ReleaseAtRequest request) {
		return papers.changeReleaseAt(id, request.releaseAt());
	}

	@GetMapping("/{id}/download")
	@PreAuthorize(HasPermission.EXAM_PAPER_READ)
	@Operation(summary = "A download link for one paper",
			description = "Returns `{url, expiresInSeconds}` with a pre-signed URL valid for 5 minutes; "
					+ "defaults to the latest version. The office and the teacher who uploaded it may download "
					+ "at any time; every other teacher only after releaseAt, and before that gets **423 "
					+ "LOCKED** with a detail like \"Available from 14 Oct 2026, 10:00\". Students and staff "
					+ "get 403. Every link issued is audited.")
	public PaperDownloadResponse download(
			@PathVariable String id,
			@RequestParam(required = false) Integer version) {
		return papers.download(id, version);
	}

	@GetMapping("/{id}/access-log")
	@PreAuthorize(HasPermission.AUDIT_READ)
	@Operation(summary = "Who touched one paper",
			description = "ADMIN only. The paper's own slice of the audit trail, newest first: every upload, "
					+ "version, release change and download, with the version in each entry.")
	public PageResponse<AuditLogResponse> accessLog(
			@PathVariable String id,
			@PageableDefault(size = 50, sort = "at", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.of(papers.accessLog(id, pageable), AuditLogResponse::from);
	}
}

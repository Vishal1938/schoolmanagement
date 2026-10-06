package com.school.notice.api;

import com.school.common.pagination.PageResponse;
import com.school.common.security.HasPermission;
import com.school.notice.app.NoticeAttachmentService;
import com.school.notice.app.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * The notice board.
 *
 * <p>{@code @PreAuthorize} here only answers "may this caller use this endpoint at all". Which
 * notices come back, and whether this caller may change the one they asked for, are decided in
 * {@link NoticeService}: the feed is filtered to the caller's audience, and a teacher is held to
 * class notices they wrote themselves.
 */
@RestController
@RequestMapping("/notices")
@Tag(name = "Notices", description = "The notice board, its audiences and its attachments")
public class NoticeController {

	private final NoticeService notices;
	private final NoticeAttachmentService attachments;

	public NoticeController(NoticeService notices, NoticeAttachmentService attachments) {
		this.notices = notices;
		this.attachments = attachments;
	}

	@GetMapping
	@PreAuthorize(HasPermission.NOTICE_READ)
	@Operation(summary = "The notices addressed to you",
			description = "Published and not expired, filtered to the caller: an admin sees every notice, a "
					+ "teacher sees ALL, TEACHERS and the class notices they wrote, a staff member sees ALL "
					+ "and STAFF, and a student sees ALL, STUDENTS and the notices for their own class. "
					+ "Pinned first, then newest; any sort parameter is ignored.")
	public PageResponse<NoticeResponse> list(Pageable pageable) {
		return PageResponse.of(notices.feed(pageable), NoticeResponse::of);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.NOTICE_WRITE_ALL_OR_CLASS)
	@Operation(summary = "Post a notice",
			description = "An admin may post to any audience. A teacher may only post CLASS notices, and may "
					+ "not make one public or pinned. publishAt defaults to now; expiresAt is optional and "
					+ "must be after it.")
	public NoticeResponse create(@Valid @RequestBody NoticeRequest request) {
		return NoticeResponse.of(notices.create(request));
	}

	@PutMapping("/{id}")
	@PreAuthorize(HasPermission.NOTICE_WRITE_ALL_OR_CLASS)
	@Operation(summary = "Replace a notice",
			description = "Same rules as posting one. A teacher may only change notices they wrote; the author "
					+ "is never reassigned by an edit.")
	public NoticeResponse update(@PathVariable String id, @Valid @RequestBody NoticeRequest request) {
		return NoticeResponse.of(notices.update(id, request));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(HasPermission.NOTICE_WRITE_ALL_OR_CLASS)
	@Operation(summary = "Delete a notice",
			description = "A teacher may only delete notices they wrote. Attachments are left in storage.")
	public void delete(@PathVariable String id) {
		notices.delete(id);
	}

	@PostMapping(path = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize(HasPermission.NOTICE_WRITE_ALL_OR_CLASS)
	@Operation(summary = "Upload an attachment",
			description = "PDF, JPEG or PNG up to 10 MB, identified by the file's own bytes rather than its "
					+ "Content-Type. Stored privately. Put the returned object into the notice's attachments "
					+ "array — uploading alone saves nothing.")
	public NoticeAttachmentResponse upload(@RequestParam("file") MultipartFile file) {
		return NoticeAttachmentResponse.of(attachments.upload(file));
	}

	/**
	 * Streams an attachment. Inline, so a PDF or a photograph opens in the browser rather than landing
	 * in the downloads folder, with the uploader's file name for whoever does save it.
	 */
	@GetMapping("/attachments/{key}")
	@PreAuthorize(HasPermission.NOTICE_READ)
	@Operation(summary = "Download an attachment",
			description = "Any logged-in user with the key, which is the unguessable URL stored on the notice. "
					+ "Streams the file; the bytes never leave this application's authentication.")
	public ResponseEntity<byte[]> download(@PathVariable String key) {
		NoticeAttachmentService.Download file = attachments.download(key);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.inline().filename(file.fileName()).build().toString())
				.cacheControl(CacheControl.noStore())
				.body(file.bytes());
	}
}

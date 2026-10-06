package com.school.notice.api;

import java.time.Instant;
import java.util.List;

import com.school.notice.domain.NoticeAudience;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A notice as written by {@code POST /notices} or {@code PUT /notices/{id}}.
 *
 * <p>What Bean Validation cannot express is checked in {@code NoticeService}: that {@code classId} is
 * present for a CLASS notice and absent otherwise, that the section belongs to the class, that
 * {@code expiresAt} is after {@code publishAt}, that every attachment URL is one this application
 * issued, and that a teacher is writing a class notice without flagging it public or pinned.
 *
 * @param body      plain text. Line breaks are kept; markup is not interpreted and must not be
 *                  rendered as HTML
 * @param classId   required when {@code audience} is CLASS, and rejected otherwise
 * @param section   optional even for a CLASS notice: null means the whole class
 * @param publishAt when it becomes visible. Null means now
 * @param expiresAt when it stops being visible. Null means never
 */
public record NoticeRequest(
		@NotBlank @Size(max = 200) String title,
		@NotBlank @Size(max = 20_000) String body,
		@NotNull NoticeAudience audience,
		@Size(max = 64) String classId,
		@Size(max = 8) String section,
		boolean isPublic,
		boolean pinned,
		@Size(max = 10) List<@Valid Attachment> attachments,
		Instant publishAt,
		Instant expiresAt) {

	/** As returned by {@code POST /notices/attachments}; pass it back verbatim. */
	public record Attachment(
			@NotBlank @Size(max = 200) String name,
			@NotBlank @Size(max = 512) String url,
			@Min(1) long size) {
	}
}

package com.school.notice.api;

import java.time.Instant;
import java.util.List;

import com.school.notice.domain.Notice;
import com.school.notice.domain.NoticeAttachment;
import com.school.notice.domain.NoticeAudience;

/**
 * A notice as the board shows it to a logged-in caller.
 *
 * <p>{@code body} is plain text with its line breaks intact — render it with {@code white-space:
 * pre-wrap}, never as HTML.
 */
public record NoticeResponse(
		String id,
		String title,
		String body,
		NoticeAudience audience,
		String classId,
		String section,
		boolean isPublic,
		boolean pinned,
		List<Attachment> attachments,
		Instant publishAt,
		Instant expiresAt,
		String authorUniqueId,
		String authorName,
		Instant createdAt) {

	public record Attachment(String name, String url, long size) {
	}

	public static NoticeResponse of(Notice notice) {
		return new NoticeResponse(
				notice.getId(),
				notice.getTitle(),
				notice.getBody(),
				notice.getAudience(),
				notice.getClassId(),
				notice.getSection(),
				notice.isPubliclyVisible(),
				notice.isPinned(),
				notice.getAttachments() == null ? List.of() : notice.getAttachments().stream()
						.map(NoticeResponse::toAttachment)
						.toList(),
				notice.getPublishAt(),
				notice.getExpiresAt(),
				notice.getAuthorUniqueId(),
				notice.getAuthorName(),
				notice.getCreatedAt());
	}

	private static Attachment toAttachment(NoticeAttachment attachment) {
		return new Attachment(attachment.name(), attachment.url(), attachment.size());
	}
}

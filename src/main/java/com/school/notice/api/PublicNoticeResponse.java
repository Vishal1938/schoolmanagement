package com.school.notice.api;

import java.time.Instant;

import com.school.notice.domain.Notice;

/**
 * A notice as an unauthenticated visitor sees it on the landing page.
 *
 * <p>Deliberately much thinner than {@link NoticeResponse}: <strong>no attachments and no author</strong>.
 * An attachment is behind an authenticated download, so publishing its URL here would only produce a
 * link that answers 401; and naming the member of staff who posted a notice is not public-safe. The
 * audience is left out too, since a public notice is by definition for everybody.
 */
public record PublicNoticeResponse(
		String id,
		String title,
		String body,
		boolean pinned,
		Instant publishAt,
		Instant expiresAt) {

	public static PublicNoticeResponse of(Notice notice) {
		return new PublicNoticeResponse(
				notice.getId(),
				notice.getTitle(),
				notice.getBody(),
				notice.isPinned(),
				notice.getPublishAt(),
				notice.getExpiresAt());
	}
}

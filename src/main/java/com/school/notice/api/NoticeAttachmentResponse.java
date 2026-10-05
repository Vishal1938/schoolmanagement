package com.school.notice.api;

import com.school.notice.domain.NoticeAttachment;

/**
 * The result of {@code POST /notices/attachments}. Put it straight into the {@code attachments} array
 * of the notice; the URL is only stored by doing so.
 *
 * @param url the download endpoint for this file, under the API base path
 */
public record NoticeAttachmentResponse(String name, String url, long size) {

	public static NoticeAttachmentResponse of(NoticeAttachment attachment) {
		return new NoticeAttachmentResponse(attachment.name(), attachment.url(), attachment.size());
	}
}

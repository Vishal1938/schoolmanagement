package com.school.accounts.api;

import com.school.accounts.domain.EntryAttachment;

/**
 * What {@code POST /accounts/entries/attachments} answers.
 *
 * <p>Send this object straight back inside the entry's {@code attachments} array. The upload on its
 * own saves nothing — the file is in storage, but no entry refers to it yet.
 */
public record EntryAttachmentResponse(String fileName, String url, long sizeBytes) {

	public static EntryAttachmentResponse of(EntryAttachment attachment) {
		return new EntryAttachmentResponse(attachment.fileName(), attachment.url(), attachment.sizeBytes());
	}
}

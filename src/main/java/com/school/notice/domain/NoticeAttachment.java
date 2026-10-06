package com.school.notice.domain;

/**
 * A file attached to a notice.
 *
 * <p>{@code url} points at this application's own download endpoint, not at the object store: the
 * file is stored privately, so it is only reachable through a request we can authenticate.
 *
 * @param name the uploader's file name, kept for display and for the download's file name
 * @param size stored size in bytes
 */
public record NoticeAttachment(String name, String url, long size) {
}

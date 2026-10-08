package com.school.exams.api;

/**
 * The answer to a download request: a pre-signed URL and how long it lasts.
 *
 * <p>Fetch it immediately and do not store it — it is a bearer of access for five minutes, which is
 * why the endpoint is audited and this object is not cacheable.
 *
 * @param url               a direct link to the object store, signed for this one object
 * @param expiresInSeconds  seconds the signature is valid for, counted from this response
 */
public record PaperDownloadResponse(String url, long expiresInSeconds) {
}

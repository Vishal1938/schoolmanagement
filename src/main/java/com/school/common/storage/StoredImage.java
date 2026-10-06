package com.school.common.storage;

/**
 * An image that is now in object storage.
 *
 * @param key         full object key, e.g. {@code public/logo/3f2c....png}
 * @param url         URL it is served from, built from {@code app.storage.public-base-url}
 * @param contentType the type detected from the file's own bytes, not from the request header
 * @param sizeBytes   stored size
 */
public record StoredImage(String key, String url, String contentType, long sizeBytes) {
}

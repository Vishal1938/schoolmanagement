package com.school.schoolconfig.api;

/**
 * The public URL of a freshly uploaded image. The admin puts it into the configuration with
 * {@code PUT /school/config}; uploading alone changes nothing.
 */
public record ImageUploadResponse(String url) {
}

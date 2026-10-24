package com.school.accounts.domain;

/**
 * A bill, an invoice or a photograph of a receipt, kept with the entry it evidences.
 *
 * @param fileName  the uploader's name, reduced to something safe to echo in a header
 * @param url       this API's own download path, not a URL into the object store: the file is
 *                  private, so every read goes through a request this application authorises
 * @param sizeBytes what was stored, for a client that wants to show it
 */
public record EntryAttachment(String fileName, String url, long sizeBytes) {
}

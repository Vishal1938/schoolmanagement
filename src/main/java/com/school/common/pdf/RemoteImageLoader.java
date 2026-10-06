package com.school.common.pdf;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fetches an image a PDF wants to embed — in practice the school logo, whose URL lives in
 * {@code school_config} and points at MinIO locally or any S3-compatible store in production.
 *
 * <p>Two properties matter more here than speed.
 *
 * <p><strong>It never fails the request.</strong> A logo that cannot be fetched comes back empty and
 * the document is produced without it, because a report card without a logo is worth a great deal
 * more than no report card.
 *
 * <p><strong>It caches.</strong> A print run of four hundred report cards wants the same bytes four
 * hundred times. Stored URLs carry a UUID and are never reused, so a successful fetch is cached for
 * the life of the process; a failure is remembered only briefly, so a store that was down for a
 * minute does not cost the logo until the next restart.
 */
@Component
public class RemoteImageLoader {

	private static final Logger log = LoggerFactory.getLogger(RemoteImageLoader.class);

	/** Short on purpose: a slow logo must not hold up a document somebody is waiting for. */
	private static final Duration TIMEOUT = Duration.ofSeconds(3);

	private static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(5);

	/** A logo is a few kilobytes; anything of this size is not something we want to embed. */
	private static final int MAX_BYTES = 2 * 1024 * 1024;

	private final HttpClient http;
	private final Clock clock;

	/** URL to the bytes, or to a failure that expires. */
	private final Map<String, Entry> cache = new ConcurrentHashMap<>();

	public RemoteImageLoader(Clock clock) {
		this.clock = clock;
		this.http = HttpClient.newBuilder()
				.connectTimeout(TIMEOUT)
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	/**
	 * The image at this URL.
	 *
	 * @return empty when the URL is missing, is not HTTP(S), or could not be fetched
	 */
	public Optional<byte[]> load(String url) {
		if (url == null || url.isBlank()) {
			return Optional.empty();
		}
		Entry cached = cache.get(url);
		if (cached != null && cached.isUsable(Instant.now(clock))) {
			return Optional.ofNullable(cached.bytes());
		}
		Optional<byte[]> fetched = fetch(url);
		cache.put(url, fetched
				.map(bytes -> new Entry(bytes, null))
				.orElseGet(() -> new Entry(null, Instant.now(clock).plus(RETRY_AFTER_FAILURE))));
		return fetched;
	}

	private Optional<byte[]> fetch(String url) {
		URI uri;
		try {
			uri = URI.create(url);
		}
		catch (IllegalArgumentException ex) {
			log.warn("Image URL in the school configuration is not a URL; the document will go out without it");
			return Optional.empty();
		}
		String scheme = uri.getScheme();
		if (!"http".equals(scheme) && !"https".equals(scheme)) {
			// Anything else — file:, jar: — would read from the server's own disk.
			log.warn("Refusing to fetch a PDF image over {}", scheme);
			return Optional.empty();
		}

		try {
			HttpResponse<byte[]> response = http.send(
					HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build(),
					HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() != 200) {
				log.warn("Image fetch for a PDF answered {}; the document will go out without it",
						response.statusCode());
				return Optional.empty();
			}
			byte[] body = response.body();
			if (body == null || body.length == 0 || body.length > MAX_BYTES) {
				log.warn("Image for a PDF is empty or larger than {} bytes; skipping it", MAX_BYTES);
				return Optional.empty();
			}
			return Optional.of(body);
		}
		catch (IOException ex) {
			log.warn("Image for a PDF could not be fetched: {}", ex.getMessage());
			return Optional.empty();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		}
	}

	/**
	 * @param bytes   the image, or null when the fetch failed
	 * @param staleAt when a failure may be retried; null for a success, which never goes stale
	 */
	private record Entry(byte[] bytes, Instant staleAt) {

		boolean isUsable(Instant now) {
			return bytes != null || staleAt != null && now.isBefore(staleAt);
		}
	}
}

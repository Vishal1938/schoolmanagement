package com.school.notice.app;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.school.common.config.AppProperties;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ValidationException;
import com.school.common.storage.ObjectStorage;
import com.school.notice.domain.NoticeAttachment;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Notice attachments: uploading them, and reading them back.
 *
 * <p>Files are stored <strong>privately</strong>, under {@code notices/}, outside the publicly
 * readable prefix. The URL stored on the notice therefore points at this application's own download
 * endpoint rather than at the object store, so every read goes through a request we authenticate.
 *
 * <p>The download endpoint streams the bytes instead of redirecting to a pre-signed URL. Both are
 * allowed by the contract; streaming is chosen because it works whatever the store's endpoint is —
 * a pre-signed URL has to be reachable by the browser, which it is not when the store sits on an
 * internal network — and because it keeps the file on our side of the authentication boundary.
 */
@Service
public class NoticeAttachmentService {

	/** Key prefix. Deliberately not under {@code app.storage.public-prefix}. */
	static final String PREFIX = "notices/";

	/** Path of the download endpoint, under the API base path. */
	static final String DOWNLOAD_PATH = "/notices/attachments/";

	/**
	 * The shape of a key this module issues: a UUID and one of the three extensions, and nothing else.
	 *
	 * <p>This is what stops the download endpoint being a way to read any object in the bucket. The
	 * key never contains a slash or a dot segment, so {@code PREFIX + key} cannot escape the prefix.
	 */
	private static final Pattern KEY = Pattern.compile("^[0-9a-f-]{36}\\.(pdf|jpg|png)$");

	/** S3 user metadata key the uploader's file name is kept under. */
	private static final String NAME_METADATA = "filename";

	/** Kept short: the name is echoed into a Content-Disposition header. */
	private static final int MAX_NAME_LENGTH = 120;

	private final ObjectStorage storage;
	private final AppProperties properties;

	public NoticeAttachmentService(ObjectStorage storage, AppProperties properties) {
		this.storage = storage;
		this.properties = properties;
	}

	/**
	 * Validates and stores one file.
	 *
	 * @throws ValidationException if it is empty, over the limit, or not a PDF, JPEG or PNG
	 */
	public NoticeAttachment upload(MultipartFile file) {
		byte[] bytes = read(file);
		AttachmentType type = identify(bytes);
		String name = safeName(file.getOriginalFilename(), type);
		String key = UUID.randomUUID() + "." + type.extension();

		storage.put(PREFIX + key, bytes, type.contentType(), Map.of(NAME_METADATA, name));
		return new NoticeAttachment(name, url(key), bytes.length);
	}

	/** The bytes of one attachment, with the name and type to serve it as. */
	public Download download(String key) {
		String validated = requireIssuedKey(key);
		ObjectStorage.StoredObject object = storage.get(PREFIX + validated);
		String name = object.metadata().getOrDefault(NAME_METADATA, validated);
		String contentType = object.contentType() == null || object.contentType().isBlank()
				? "application/octet-stream"
				: object.contentType();
		return new Download(object.bytes(), contentType, name);
	}

	/**
	 * Checks that a URL on an incoming notice is one of ours.
	 *
	 * <p>Without this, a notice could carry a link to anywhere and the board would be a redirect
	 * service. The URL has to be the download path of a key this module issued; the object does not
	 * have to still exist, because a notice outliving a deleted file is a broken link, not a breach.
	 *
	 * @throws ValidationException with {@code field} pointing at the offending attachment
	 */
	public void requireOwnUrl(String url, String field) {
		String base = url(null);
		if (url == null || !url.startsWith(base)) {
			throw new ValidationException("That attachment was not uploaded here",
					List.of(new FieldViolation(field, "must be a URL returned by POST /notices/attachments")));
		}
		String key = url.substring(base.length());
		if (!KEY.matcher(key).matches()) {
			throw new ValidationException("That attachment URL is not usable",
					List.of(new FieldViolation(field, "does not name an uploaded file")));
		}
	}

	/** An attachment on its way back to the caller. */
	public record Download(byte[] bytes, String contentType, String fileName) {
	}

	// --- internals --------------------------------------------------------------------------------

	/** The download URL for a key, or the base URL when {@code key} is null. */
	private String url(String key) {
		return properties.apiBasePath() + DOWNLOAD_PATH + (key == null ? "" : key);
	}

	private String requireIssuedKey(String key) {
		if (key == null || !KEY.matcher(key).matches()) {
			// Not a 404: the key is malformed, so there is nothing to look for.
			throw new ValidationException("That is not an attachment key",
					List.of(new FieldViolation("key", "must be a key issued by this application")));
		}
		return key;
	}

	private byte[] read(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ValidationException("The file is missing or empty",
					List.of(new FieldViolation("file", "must not be empty")));
		}
		long limit = properties.storage().maxAttachmentSize().toBytes();
		if (file.getSize() > limit) {
			throw new ValidationException("The file is too large", List.of(new FieldViolation("file",
					"must not be larger than " + properties.storage().maxAttachmentSize().toMegabytes() + " MB")));
		}
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw new ValidationException("The file could not be read",
					List.of(new FieldViolation("file", "could not be read")));
		}
	}

	private AttachmentType identify(byte[] bytes) {
		return AttachmentType.detect(bytes).orElseThrow(() -> new ValidationException(
				"That file type is not accepted",
				List.of(new FieldViolation("file", "must be a PDF, JPEG or PNG file"))));
	}

	/**
	 * The uploader's file name, reduced to something safe to store as S3 metadata and to echo in a
	 * {@code Content-Disposition} header: no directory parts, ASCII only, and never empty.
	 */
	private static String safeName(String originalName, AttachmentType type) {
		String fallback = "attachment." + type.extension();
		if (originalName == null || originalName.isBlank()) {
			return fallback;
		}
		String base = originalName.trim();
		int lastSeparator = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
		if (lastSeparator >= 0) {
			base = base.substring(lastSeparator + 1);
		}
		// Anything outside this set — accents, quotes, control characters, header separators — becomes
		// an underscore rather than being carried into a response header.
		base = base.replaceAll("[^A-Za-z0-9 ._()-]", "_").trim();
		if (base.length() > MAX_NAME_LENGTH) {
			base = base.substring(0, MAX_NAME_LENGTH);
		}
		if (base.isBlank() || base.equals(".") || base.equals("..")) {
			return fallback;
		}
		return base.toLowerCase(Locale.ROOT).endsWith("." + type.extension())
				? base
				: base + "." + type.extension();
	}
}

package com.school.notice.app;

import java.util.Arrays;
import java.util.Optional;

/**
 * The file types a notice may carry, each identified by its own leading bytes rather than by the
 * {@code Content-Type} or the extension the client claims.
 *
 * <p>A circular, a form to fill in, a photograph of a notice board: a PDF and the two photo formats
 * cover all of it. Everything else is refused, which is why an uploaded file can never be an Office
 * macro document, an archive or an SVG.
 */
public enum AttachmentType {

	PDF("application/pdf", "pdf", new int[] {'%', 'P', 'D', 'F'}),
	JPEG("image/jpeg", "jpg", new int[] {0xFF, 0xD8, 0xFF}),
	PNG("image/png", "png", new int[] {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

	private final String contentType;
	private final String extension;
	private final int[] magic;

	AttachmentType(String contentType, String extension, int[] magic) {
		this.contentType = contentType;
		this.extension = extension;
		this.magic = magic;
	}

	public String contentType() {
		return contentType;
	}

	public String extension() {
		return extension;
	}

	/** Identifies the type from the start of the file, or empty when it is not one of these three. */
	public static Optional<AttachmentType> detect(byte[] bytes) {
		return Arrays.stream(values()).filter(type -> type.matches(bytes)).findFirst();
	}

	private boolean matches(byte[] bytes) {
		if (bytes.length < magic.length) {
			return false;
		}
		for (int i = 0; i < magic.length; i++) {
			if ((bytes[i] & 0xFF) != magic[i]) {
				return false;
			}
		}
		return true;
	}
}

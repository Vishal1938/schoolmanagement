package com.school.common.storage;

import java.util.Arrays;
import java.util.Optional;

/**
 * The image formats accepted for upload, each identified by its own leading bytes rather than by the
 * {@code Content-Type} the client claims.
 *
 * <p>SVG is deliberately absent. It is XML, it can carry script, and these files are served from the
 * same origin as the application, so an uploaded SVG would be stored cross-site scripting.
 */
public enum ImageType {

	PNG("image/png", "png", new int[] {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
	JPEG("image/jpeg", "jpg", new int[] {0xFF, 0xD8, 0xFF}),
	GIF("image/gif", "gif", new int[] {'G', 'I', 'F', '8'}),
	ICO("image/x-icon", "ico", new int[] {0x00, 0x00, 0x01, 0x00}),
	/** RIFF....WEBP — the four bytes at offset 8 are checked separately. */
	WEBP("image/webp", "webp", new int[] {'R', 'I', 'F', 'F'});

	private final String contentType;
	private final String extension;
	private final int[] magic;

	ImageType(String contentType, String extension, int[] magic) {
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

	/** Identifies the format from the start of the file, or empty when it is not a supported image. */
	public static Optional<ImageType> detect(byte[] bytes) {
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
		// RIFF also fronts .wav and .avi, so WebP needs its form type as well.
		return this != WEBP || (bytes.length >= 12
				&& bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P');
	}
}

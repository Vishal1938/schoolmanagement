package com.school.accounts.app;

import java.util.Arrays;
import java.util.Optional;

/**
 * The file types a bill may be scanned or photographed as, each identified by its own leading bytes
 * rather than by the {@code Content-Type} or the extension the client claims.
 *
 * <p>A PDF invoice and a phone photograph of a cash memo cover what an office actually has.
 * Everything else is refused, which is why an uploaded bill can never be an Office macro document,
 * an archive or an SVG.
 *
 * <p>Deliberately a copy of the notice module's equivalent rather than a shared class. Three magic
 * numbers are not worth a dependency from the books to the notice board, and {@code common} is the
 * wrong home for it either — the two lists are free to diverge, and a file type the notice board
 * accepts is not automatically one a bill may be.
 */
public enum BillFileType {

	PDF("application/pdf", "pdf", new int[] {'%', 'P', 'D', 'F'}),
	JPEG("image/jpeg", "jpg", new int[] {0xFF, 0xD8, 0xFF}),
	PNG("image/png", "png", new int[] {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

	private final String contentType;
	private final String extension;
	private final int[] magic;

	BillFileType(String contentType, String extension, int[] magic) {
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
	public static Optional<BillFileType> detect(byte[] bytes) {
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

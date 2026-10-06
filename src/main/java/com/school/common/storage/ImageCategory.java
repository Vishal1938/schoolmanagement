package com.school.common.storage;

/**
 * What a public image is for. The value becomes the folder under the public prefix, so a bucket stays
 * browsable: {@code public/logo/...}, {@code public/gallery/...}.
 */
public enum ImageCategory {

	LOGO("logo"),
	FAVICON("favicon"),
	GALLERY("gallery");

	private final String folder;

	ImageCategory(String folder) {
		this.folder = folder;
	}

	public String folder() {
		return folder;
	}
}

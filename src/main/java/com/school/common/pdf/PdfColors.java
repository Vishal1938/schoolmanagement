package com.school.common.pdf;

import java.awt.Color;

/**
 * Turns the CSS hex colours of the school theme into the AWT colours OpenPDF draws with, so a PDF is
 * branded from {@code school_config} like every other surface and no colour is hardcoded.
 *
 * <p>A colour that cannot be read falls back rather than failing: a report card in the default blue
 * is worth more than a 500 because somebody typed a bad hex.
 */
public final class PdfColors {

	private PdfColors() {
	}

	/**
	 * Parses {@code #abc} or {@code #aabbcc}, with or without the hash.
	 *
	 * @param fallback used when {@code hex} is null, blank or not a hex colour
	 */
	public static Color of(String hex, Color fallback) {
		if (hex == null) {
			return fallback;
		}
		String digits = hex.trim();
		if (digits.startsWith("#")) {
			digits = digits.substring(1);
		}
		if (digits.length() == 3) {
			// #abc is #aabbcc: each digit doubled, which is the CSS rule.
			StringBuilder expanded = new StringBuilder(6);
			for (int i = 0; i < 3; i++) {
				expanded.append(digits.charAt(i)).append(digits.charAt(i));
			}
			digits = expanded.toString();
		}
		if (digits.length() != 6) {
			return fallback;
		}
		try {
			return new Color(Integer.parseInt(digits, 16));
		}
		catch (NumberFormatException ex) {
			return fallback;
		}
	}
}

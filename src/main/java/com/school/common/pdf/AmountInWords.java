package com.school.common.pdf;

/**
 * Money in words, in the Indian system: crore, lakh, thousand, hundred.
 *
 * <p>A receipt (B12) and a salary slip (B13) both state the amount twice, in figures and in words,
 * because that is what makes a document hard to alter after it has been handed over — and the words
 * have to group the Indian way, so 1,50,000 reads as "One Lakh Fifty Thousand" and never as "One
 * Hundred Fifty Thousand". Here in {@code common} rather than in either module, since both print it.
 *
 * <p>Pure and static. Paise in, English out, no formatting of the figure itself.
 */
public final class AmountInWords {

	private static final String[] ONES = {
			"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
			"Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen",
			"Nineteen"};

	private static final String[] TENS = {
			"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

	private static final long CRORE = 1_00_00_000L;
	private static final long LAKH = 1_00_000L;
	private static final long THOUSAND = 1_000L;

	/**
	 * The full phrase a receipt prints, e.g. {@code "Rupees One Lakh Fifty Thousand and Fifty Paise
	 * Only"}.
	 *
	 * <p>The paise clause is left out when the amount is whole, which is almost always: school fees
	 * are set in whole rupees, and "and Zero Paise" on every receipt would be noise.
	 *
	 * @param paise the amount, in paise; negative is treated as zero rather than throwing, because a
	 *              receipt must never fail to print over a figure
	 */
	public static String rupees(long paise) {
		long safe = Math.max(0L, paise);
		long rupees = safe / 100L;
		long fraction = safe % 100L;
		StringBuilder phrase = new StringBuilder("Rupees ").append(words(rupees));
		if (fraction > 0L) {
			phrase.append(" and ").append(words(fraction)).append(" Paise");
		}
		return phrase.append(" Only").toString();
	}

	/** A whole number in words, with no currency around it. {@code 0} is "Zero". */
	public static String words(long value) {
		if (value <= 0L) {
			return "Zero";
		}
		StringBuilder out = new StringBuilder();
		long rest = value;

		// Recursive for the crore part, so "One Hundred Twenty Three Crore" works without a separate
		// scale name for every power above it.
		if (rest >= CRORE) {
			out.append(words(rest / CRORE)).append(" Crore ");
			rest %= CRORE;
		}
		// Below a crore, each of these groups is 1–99, so two digits is always enough.
		if (rest >= LAKH) {
			out.append(underHundred(rest / LAKH)).append(" Lakh ");
			rest %= LAKH;
		}
		if (rest >= THOUSAND) {
			out.append(underHundred(rest / THOUSAND)).append(" Thousand ");
			rest %= THOUSAND;
		}
		if (rest >= 100L) {
			out.append(ONES[(int) (rest / 100L)]).append(" Hundred ");
			rest %= 100L;
		}
		if (rest > 0L) {
			out.append(underHundred(rest));
		}
		return out.toString().trim().replaceAll("\\s{2,}", " ");
	}

	/** 1–99. The teens have their own names, so anything under 20 is a straight lookup. */
	private static String underHundred(long value) {
		if (value < 20L) {
			return ONES[(int) value];
		}
		String tens = TENS[(int) (value / 10L)];
		long unit = value % 10L;
		return unit == 0L ? tens : tens + " " + ONES[(int) unit];
	}

	private AmountInWords() {
	}
}

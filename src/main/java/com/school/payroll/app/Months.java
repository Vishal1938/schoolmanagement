package com.school.payroll.app;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ValidationException;

/**
 * Payroll's one month format, {@code yyyy-MM}.
 *
 * <p>Stored on the record as that string rather than as a date, because it is what every query and
 * sort wants: "2026-10" sorts chronologically as text, ranges with plain comparisons, and reads
 * correctly in a URL, a JSON body and a Mongo document without a timezone coming into it.
 */
final class Months {

	/** For the salary slip heading: "October 2026". */
	static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

	/**
	 * Parses a month from a request or a path.
	 *
	 * @throws ValidationException 400 for anything that is not a month. The bean-validation pattern on
	 *                             the request body catches most of it; a path variable has no such
	 *                             annotation, so this is the only check there is
	 */
	static YearMonth parse(String month, String field) {
		if (month == null || month.isBlank()) {
			throw new ValidationException("A month is required",
					List.of(new FieldViolation(field, "must be a month as yyyy-MM, e.g. 2026-10")));
		}
		try {
			return YearMonth.parse(month.trim());
		}
		catch (DateTimeParseException ex) {
			throw new ValidationException("That is not a month",
					List.of(new FieldViolation(field, month + " is not a month as yyyy-MM")));
		}
	}

	/** The canonical {@code yyyy-MM} string for a month. */
	static String format(YearMonth month) {
		return month.toString();
	}

	private Months() {
	}
}

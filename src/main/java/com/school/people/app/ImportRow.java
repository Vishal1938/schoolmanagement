package com.school.people.app;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.school.people.api.ImportError;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

/**
 * One data row, and the accessors the import services read it through.
 *
 * <p>Each accessor <strong>records its own problem and returns null</strong> rather than throwing,
 * which is what lets a single pass collect every mistake in the file. A caller therefore has to
 * expect nulls back from {@code required…} too, and ask {@link #ok()} before using what it read; the
 * alternative — stopping at the first bad cell — would have an administrator fixing a 300-row
 * spreadsheet one error per upload.
 *
 * <p>The length limits mirror the ones Bean Validation puts on {@code StudentRequest} and
 * {@code EmployeeRequest}. An imported row never passes through {@code @Valid}, so they are checked
 * here instead and nothing reaches the database that a typed-in form could not.
 */
final class ImportRow {

	/** Ten digits, which is what Indian mobile numbers are and what the templates ask for. */
	private static final Pattern TEN_DIGITS = Pattern.compile("^[0-9]{10}$");

	/** Separators people sprinkle through phone and account numbers, removed before counting digits. */
	private static final Pattern SEPARATORS = Pattern.compile("[\\s()./-]");

	/** Deliberately loose. The authority on an address is the mail server, not a regular expression. */
	private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

	private final Row row;
	private final Map<String, Integer> columns;
	private final List<ImportError> errors;

	/**
	 * The row number as Excel shows it, taken once at construction rather than read from the POI row.
	 * Cross-row checks run after the workbook has been closed, and they still name their rows.
	 */
	private final int number;

	/** How many of the shared errors are this row's. Counted here because the list is everyone's. */
	private int ownErrors;

	ImportRow(Row row, Map<String, Integer> columns, List<ImportError> errors) {
		this.row = row;
		this.columns = columns;
		this.errors = errors;
		this.number = row.getRowNum() + 1;
	}

	/** The row number as Excel shows it: the header is 1 and the first record is 2. */
	int number() {
		return number;
	}

	/** Whether nothing has gone wrong in <em>this</em> row. */
	boolean ok() {
		return ownErrors == 0;
	}

	void error(String column, String message) {
		errors.add(new ImportError(number, column, message));
		ownErrors++;
	}

	// --- text -------------------------------------------------------------------------------------

	String optional(String column, int maxLength) {
		String value = ImportSheet.text(cell(column));
		if (value != null && value.length() > maxLength) {
			error(column, "must be at most " + maxLength + " characters");
			return null;
		}
		return value;
	}

	String required(String column, int maxLength) {
		int before = ownErrors;
		String value = optional(column, maxLength);
		if (value == null && ownErrors == before) {
			error(column, "is required");
		}
		return value;
	}

	/** Lower-cased, as e-mail addresses are stored everywhere else. */
	String optionalEmail(String column) {
		String value = optional(column, 200);
		if (value == null) {
			return null;
		}
		if (!EMAIL.matcher(value).matches()) {
			error(column, "is not a valid e-mail address");
			return null;
		}
		return value.toLowerCase(Locale.ROOT);
	}

	// --- enums ------------------------------------------------------------------------------------

	<E extends Enum<E>> E optionalEnum(String column, Class<E> type) {
		String value = optional(column, 40);
		if (value == null) {
			return null;
		}
		try {
			return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			error(column, "must be one of " + allowed(type));
			return null;
		}
	}

	<E extends Enum<E>> E requiredEnum(String column, Class<E> type) {
		int before = ownErrors;
		E value = optionalEnum(column, type);
		if (value == null && ownErrors == before) {
			error(column, "is required and must be one of " + allowed(type));
		}
		return value;
	}

	/** {@code YES}/{@code NO}, as the template's dropdown offers. Null when the cell is empty. */
	Boolean optionalYesNo(String column) {
		String value = optional(column, 10);
		if (value == null) {
			return null;
		}
		return switch (value.trim().toUpperCase(Locale.ROOT)) {
			case "YES", "Y", "TRUE", "1" -> Boolean.TRUE;
			case "NO", "N", "FALSE", "0" -> Boolean.FALSE;
			default -> {
				error(column, "must be YES or NO");
				yield null;
			}
		};
	}

	// --- dates ------------------------------------------------------------------------------------

	/**
	 * A date, whether the cell held a real Excel date or the text the template asks for.
	 * {@link ImportSheet} has already rendered the former into {@code DD-MM-YYYY}, so there is one
	 * format to parse; {@code /} and {@code .} are accepted as separators because people type them.
	 */
	LocalDate optionalDate(String column) {
		String value = optional(column, 32);
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(value.replace('/', '-').replace('.', '-'), ImportSheet.DATE_FORMAT);
		}
		catch (DateTimeParseException ex) {
			error(column, "must be a date in DD-MM-YYYY format, e.g. 14-06-2015");
			return null;
		}
	}

	LocalDate requiredDate(String column) {
		int before = ownErrors;
		LocalDate value = optionalDate(column);
		if (value == null && ownErrors == before) {
			error(column, "is required, in DD-MM-YYYY format");
		}
		return value;
	}

	/** A date that cannot be in the future — a birth date, or the day somebody joined. */
	LocalDate pastDate(String column, LocalDate today, boolean required) {
		LocalDate value = required ? requiredDate(column) : optionalDate(column);
		if (value != null && value.isAfter(today)) {
			error(column, "cannot be in the future");
			return null;
		}
		return value;
	}

	// --- numbers ----------------------------------------------------------------------------------

	Integer optionalInt(String column, int min, int max) {
		String value = optional(column, 16);
		if (value == null) {
			return null;
		}
		int parsed;
		try {
			parsed = Integer.parseInt(value.trim());
		}
		catch (NumberFormatException ex) {
			error(column, "must be a whole number");
			return null;
		}
		if (parsed < min || parsed > max) {
			error(column, "must be between " + min + " and " + max);
			return null;
		}
		return parsed;
	}

	/** Digits only, between {@code min} and {@code max} of them — a bank account number. */
	String optionalDigits(String column, int min, int max) {
		String value = optional(column, max + 8);
		if (value == null) {
			return null;
		}
		String digits = SEPARATORS.matcher(value).replaceAll("");
		if (!digits.matches("^[0-9]{" + min + "," + max + "}$")) {
			error(column, "must be " + min + "-" + max + " digits");
			return null;
		}
		return digits;
	}

	// --- phones -----------------------------------------------------------------------------------

	String optionalPhone(String column) {
		String value = optional(column, 32);
		if (value == null) {
			return null;
		}
		String digits = SEPARATORS.matcher(value).replaceAll("");
		if (!TEN_DIGITS.matcher(digits).matches()) {
			error(column, "must be a 10-digit phone number");
			return null;
		}
		return digits;
	}

	String requiredPhone(String column) {
		int before = ownErrors;
		String value = optionalPhone(column);
		if (value == null && ownErrors == before) {
			error(column, "is required, as 10 digits");
		}
		return value;
	}

	// --- internals --------------------------------------------------------------------------------

	private Cell cell(String column) {
		Integer index = columns.get(ImportSheet.normalize(column));
		return index == null ? null : row.getCell(index);
	}

	private static <E extends Enum<E>> String allowed(Class<E> type) {
		return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
	}
}

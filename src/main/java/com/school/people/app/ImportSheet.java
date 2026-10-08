package com.school.people.app;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ValidationException;
import com.school.people.api.ImportError;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

/**
 * The first sheet of an uploaded workbook, read as text.
 *
 * <p><strong>Every cell is read as a string</strong>, whatever Excel stored it as. That is the whole
 * point of this class: a ten-digit phone number typed into a General cell is a {@code double} by the
 * time it reaches us, and {@code Double.toString} would turn it into {@code 9.8765432E9}. Going
 * through {@link BigDecimal#toPlainString()} keeps every digit and drops the {@code .0} that a whole
 * number would otherwise acquire.
 *
 * <p>Dates are the other half of the same problem. A cell may hold a real Excel date or the text
 * {@code 14-06-2015}, and an administrator editing the template will produce both in one column. A
 * real date is formatted to {@code dd-MM-yyyy} here, so by the time anything is parsed there is only
 * one shape to parse.
 *
 * <p>Columns are addressed by header text rather than by position, so an admin who reorders the
 * template's columns — or deletes the asterisks — still gets their file read.
 */
final class ImportSheet implements AutoCloseable {

	/** The one date format the import accepts, and the one real dates are rendered into. */
	static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-uuuu");

	/**
	 * Rows past this are refused rather than imported. A school admits a few hundred students at a
	 * time; a file with thousands of rows is a mistake, and validating it would hold a request and a
	 * MongoDB transaction open for minutes.
	 */
	static final int MAX_ROWS = 1000;

	/** 5 MB, as the task specifies. Well under the 25 MB multipart transport limit. */
	private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;

	private final Workbook workbook;
	private final Sheet sheet;

	/** Normalized header text to column index, in the order the sheet has them. */
	private final Map<String, Integer> columns;

	private ImportSheet(Workbook workbook, Sheet sheet, Map<String, Integer> columns) {
		this.workbook = workbook;
		this.sheet = sheet;
		this.columns = columns;
	}

	/**
	 * The bytes of an uploaded import file, checked against the 5 MB limit.
	 *
	 * <p>Read whole into memory on purpose: the sheet has to be validated end to end before anything
	 * is written, so it is read twice whatever happens, and 5 MB of XLSX is a few thousand rows.
	 *
	 * @throws ValidationException 400 if the upload is missing, empty or over the limit
	 */
	static byte[] bytesOf(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ValidationException("The import file is missing or empty",
					List.of(new FieldViolation("file", "must be an .xlsx workbook")));
		}
		if (file.getSize() > MAX_UPLOAD_BYTES) {
			throw new ValidationException("The import file is too large",
					List.of(new FieldViolation("file", "must not be larger than 5 MB")));
		}
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw new ValidationException("The import file could not be read",
					List.of(new FieldViolation("file", "could not be read")));
		}
	}

	/**
	 * Opens the first sheet of an {@code .xlsx} file.
	 *
	 * @throws ValidationException 400 if the bytes are not a readable workbook, or it has no sheets.
	 *                             This is a problem with the file rather than with a row, so it is not
	 *                             reported as an {@link ImportError}
	 */
	static ImportSheet open(byte[] bytes) {
		Workbook workbook;
		try {
			workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes));
		}
		catch (IOException | RuntimeException ex) {
			// RuntimeException on purpose: POI throws several unrelated unchecked types — EmptyFileException,
			// NotOfficeXmlFileException, POIXMLException — for a file that is simply not a workbook.
			throw new ValidationException("The file could not be read as a spreadsheet",
					List.of(new FieldViolation("file", "must be an .xlsx workbook saved from the template")));
		}
		if (workbook.getNumberOfSheets() == 0) {
			closeQuietly(workbook);
			throw new ValidationException("The workbook has no sheets",
					List.of(new FieldViolation("file", "must have the data on its first sheet")));
		}
		Sheet sheet = workbook.getSheetAt(0);
		return new ImportSheet(workbook, sheet, headers(sheet));
	}

	/**
	 * The expected headers this sheet does not have, as errors against the header row.
	 *
	 * <p>Reported the same way a bad cell is, rather than as a 400, because from the admin's side it
	 * is the same mistake: something about the spreadsheet needs fixing before it can be imported.
	 */
	List<ImportError> missingColumns(List<String> expected) {
		return expected.stream()
				.filter(column -> !columns.containsKey(normalize(column)))
				.map(column -> new ImportError(1, column, "this column is missing from the sheet"))
				.toList();
	}

	/**
	 * The data rows, skipping the header and any row whose cells are all blank — a template that has
	 * been scrolled through usually ends with a few of those, and they are not records.
	 *
	 * @param errors collector every row shares, so one pass gathers every problem in the file
	 */
	List<ImportRow> rows(List<ImportError> errors) {
		List<ImportRow> rows = new ArrayList<>();
		for (int i = 1; i <= sheet.getLastRowNum(); i++) {
			Row row = sheet.getRow(i);
			if (row == null || isBlank(row)) {
				continue;
			}
			rows.add(new ImportRow(row, columns, errors));
			if (rows.size() > MAX_ROWS) {
				errors.add(new ImportError(1, "file",
						"an import may hold at most " + MAX_ROWS + " rows; split the file and try again"));
				return List.of();
			}
		}
		if (rows.isEmpty()) {
			errors.add(new ImportError(1, "file", "the sheet has a header row but no data rows"));
		}
		return rows;
	}

	@Override
	public void close() {
		closeQuietly(workbook);
	}

	// --- cell reading -----------------------------------------------------------------------------

	/**
	 * One cell as trimmed text, or null when it is empty.
	 *
	 * <p>A formula is read from its cached result rather than evaluated: the workbook came from
	 * outside, and evaluating arbitrary formulas out of an uploaded file is work we do not need to do.
	 */
	static String text(Cell cell) {
		if (cell == null) {
			return null;
		}
		CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
		String value = switch (type) {
			case STRING -> cell.getStringCellValue();
			case BOOLEAN -> cell.getBooleanCellValue() ? "YES" : "NO";
			case NUMERIC -> numericText(cell);
			default -> null;
		};
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String numericText(Cell cell) {
		if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
			return cell.getLocalDateTimeCellValue().toLocalDate().format(DATE_FORMAT);
		}
		// toPlainString, never Double.toString: a phone number must come out of a General cell with
		// all ten of its digits rather than as 9.8765432E9.
		return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
	}

	/** Headers reduced to a comparison key: no asterisk, no case, no runs of spaces. */
	static String normalize(String header) {
		return header.replace("*", "").trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private static Map<String, Integer> headers(Sheet sheet) {
		Map<String, Integer> columns = new LinkedHashMap<>();
		Row header = sheet.getRow(0);
		if (header == null) {
			return columns;
		}
		for (int i = 0; i < header.getLastCellNum(); i++) {
			String text = text(header.getCell(i));
			if (text != null) {
				// First wins, so a stray duplicate header further right cannot shadow the real column.
				columns.putIfAbsent(normalize(text), i);
			}
		}
		return columns;
	}

	private static boolean isBlank(Row row) {
		for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
			if (text(row.getCell(i)) != null) {
				return false;
			}
		}
		return true;
	}

	private static void closeQuietly(Workbook workbook) {
		try {
			workbook.close();
		}
		catch (IOException ignored) {
			// Nothing useful to do: the bytes were in memory and the request is over either way.
		}
	}
}

package com.school.people.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.school.academics.app.SchoolClassService;
import com.school.academics.app.SubjectService;
import com.school.academics.domain.SchoolClass;
import com.school.academics.domain.Subject;
import com.school.common.exceptions.AppException;
import com.school.common.exceptions.ErrorType;
import com.school.people.domain.EmployeeType;
import com.school.people.domain.Gender;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import static com.school.people.app.ImportColumns.Employees;
import static com.school.people.app.ImportColumns.Students;

/**
 * Builds the two import templates, filled in with this school's own classes, sections and subjects.
 *
 * <p>The templates are generated rather than shipped as files because the dropdowns have to offer
 * what actually exists here — a static workbook would list somebody else's classes, and CLAUDE.md
 * rule 6 rules that out. They are also the documentation: an administrator should be able to open
 * the file and fill it in without reading anything else, which is what the Instructions sheet is
 * for.
 *
 * <p>Dropdowns are formula constraints pointing at columns on the Instructions sheet, not inline
 * lists. Excel caps an inline list at 255 characters, which a school with thirty classes would
 * quietly exceed; a range has no such limit, and it has the pleasant side effect that the allowed
 * values are visible rather than hidden in the file's validation rules.
 *
 * <p>The Section dropdown offers every section in the school, because a single validation rule
 * cannot depend on the class chosen two cells to the left. The server checks that the section
 * actually belongs to the class, which is the check that matters.
 */
@Service
public class ImportTemplates {

	/** Rows the dropdowns and the text formatting are applied to. Beyond this the sheet is plain. */
	private static final int VALIDATED_ROWS = 500;

	/** First column of the lookup block on the Instructions sheet: far enough right to stay out of the way. */
	private static final int LOOKUP_FIRST_COLUMN = 4;

	private static final String INSTRUCTIONS = "Instructions";

	private final SchoolClassService classes;
	private final SubjectService subjects;

	public ImportTemplates(SchoolClassService classes, SubjectService subjects) {
		this.classes = classes;
		this.subjects = subjects;
	}

	// --- students ---------------------------------------------------------------------------------

	/** {@code GET /students/import/template}. */
	public byte[] students() {
		List<SchoolClass> schoolClasses = classes.list();
		List<String> classNames = schoolClasses.stream().map(SchoolClass::getName).toList();
		List<String> sections = allSections(schoolClasses);
		String exampleClass = classNames.isEmpty() ? "" : classNames.getFirst();
		String exampleSection = sections.isEmpty() ? "" : sections.getFirst();

		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet data = dataSheet(workbook, "Students", Students.ALL, List.of(
					"Aarav Sharma", "14-06-2015", Gender.MALE.name(), exampleClass, exampleSection, "12",
					"2026/118", "05-04-2026", "Rakesh Sharma", "Sunita Sharma", "9876543210", "9812345670",
					"aarav.sharma@example.com", "12 MG Road, Bhopal", "O+", "Little Angels School"));
			asText(workbook, data, Students.ALL,
					List.of(Students.ROLL_NO, Students.ADMISSION_NO, Students.GUARDIAN_PHONE,
							Students.ALTERNATE_PHONE));

			Sheet help = instructionsSheet(workbook, List.of(
					"How to use this template",
					"",
					"1. Put one student per row on the Students sheet, starting at row 2.",
					"2. Do not rename, delete or reorder the columns. Columns marked * must be filled in.",
					"3. Dates must be written DD-MM-YYYY, for example 14-06-2015. A real Excel date works too.",
					"4. Phone numbers must be exactly 10 digits, and are kept as text so no digits are lost.",
					"5. Class and Section must already exist in the school. Pick them from the dropdowns.",
					"   The Section list covers the whole school, so make sure the one you pick belongs to",
					"   the class you picked - the server checks this and will reject the row if it does not.",
					"6. Roll No is optional. Leave it blank and the next free number in that section is used.",
					"7. Admission No is optional. Leave it blank and one is generated. If you do fill it in,",
					"   it must not already be used by another student, or appear twice in this file.",
					"8. Admission Date is optional and defaults to the day of the import.",
					"",
					"What happens when you upload it",
					"",
					"The whole file is checked before anything is saved. If any row has a mistake, NOTHING is",
					"created and you get back a list of every bad cell, with its row number and column.",
					"Fix them all and upload again.",
					"",
					"When the import succeeds, every student gets a login and a temporary password. Those",
					"passwords are in the credentials file the response points at. Download it straight away:",
					"it is deleted automatically after 24 hours and the passwords cannot be read back.",
					"",
					"Allowed values for the dropdown columns are listed to the right."));

			lookup(help, 0, "Gender", names(Gender.values()));
			lookup(help, 1, "Class", classNames);
			lookup(help, 2, "Section", sections);

			dropdown(data, Students.ALL.indexOf(Students.GENDER), lookupRange(0, Gender.values().length));
			dropdown(data, Students.ALL.indexOf(Students.SCHOOL_CLASS), lookupRange(1, classNames.size()));
			dropdown(data, Students.ALL.indexOf(Students.SECTION), lookupRange(2, sections.size()));

			return toBytes(workbook);
		}
		catch (IOException ex) {
			throw new AppException(ErrorType.DEPENDENCY_FAILED, "The template could not be built", ex);
		}
	}

	// --- employees --------------------------------------------------------------------------------

	/** {@code GET /employees/import/template}. */
	public byte[] employees() {
		List<String> subjectNames = subjects.list().stream().map(Subject::getName).toList();
		String exampleSubjects = String.join(", ", subjectNames.stream().limit(2).toList());

		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet data = dataSheet(workbook, "Employees", Employees.ALL, List.of(
					EmployeeType.TEACHER.name(), "Priya Verma", "02-11-1990", Gender.FEMALE.name(), "9876501234",
					"priya.verma@example.com", "01-04-2026", "", "M.Sc, B.Ed", exampleSubjects, "",
					"Priya Verma", "12345678901", "SBIN0001234", "State Bank of India"));
			asText(workbook, data, Employees.ALL, List.of(Employees.PHONE, Employees.ACCOUNT_NUMBER));

			Sheet help = instructionsSheet(workbook, List.of(
					"How to use this template",
					"",
					"1. Put one employee per row on the Employees sheet, starting at row 2.",
					"2. Do not rename, delete or reorder the columns. Columns marked * must be filled in.",
					"3. Dates must be written DD-MM-YYYY, for example 01-04-2026. A real Excel date works too.",
					"4. Phone must be exactly 10 digits, and is kept as text so no digits are lost.",
					"",
					"Which columns apply to whom",
					"",
					"Type decides this. For a TEACHER, fill in Qualification and Subjects; Designation and",
					"Can Login are ignored, because a teacher always gets a login. For a STAFF member, fill",
					"in Designation and Can Login; Qualification and Subjects are ignored.",
					"",
					"Subjects is a comma-separated list of subject names, spelled exactly as they are in the",
					"school's subject list - they are shown to the right. A name that is not in that list is",
					"an error, not a new subject.",
					"",
					"Bank columns are all optional. If you give an Account Number it must be 6 to 20 digits;",
					"it is stored encrypted and only its last four digits ever appear in a list.",
					"",
					"What happens when you upload it",
					"",
					"The whole file is checked before anything is saved. If any row has a mistake, NOTHING is",
					"created and you get back a list of every bad cell, with its row number and column.",
					"Fix them all and upload again.",
					"",
					"Everyone who gets a login gets a temporary password, and those are in the credentials",
					"file the response points at. Download it straight away: it is deleted after 24 hours.",
					"",
					"Allowed values for the dropdown columns are listed to the right."));

			lookup(help, 0, "Type", names(EmployeeType.values()));
			lookup(help, 1, "Gender", names(Gender.values()));
			lookup(help, 2, "Can Login", List.of("YES", "NO"));
			lookup(help, 3, "Subject names", subjectNames);

			dropdown(data, Employees.ALL.indexOf(Employees.TYPE), lookupRange(0, EmployeeType.values().length));
			dropdown(data, Employees.ALL.indexOf(Employees.GENDER), lookupRange(1, Gender.values().length));
			dropdown(data, Employees.ALL.indexOf(Employees.CAN_LOGIN), lookupRange(2, 2));

			return toBytes(workbook);
		}
		catch (IOException ex) {
			throw new AppException(ErrorType.DEPENDENCY_FAILED, "The template could not be built", ex);
		}
	}

	// --- sheet building ---------------------------------------------------------------------------

	/** The data sheet: a frozen, highlighted header row and one example row below it. */
	private static Sheet dataSheet(Workbook workbook, String name, List<String> headers, List<String> example) {
		Sheet sheet = workbook.createSheet(name);
		CellStyle headerStyle = headerStyle(workbook);

		Row header = sheet.createRow(0);
		Row sample = sheet.createRow(1);
		for (int i = 0; i < headers.size(); i++) {
			Cell cell = header.createCell(i);
			cell.setCellValue(headers.get(i));
			cell.setCellStyle(headerStyle);
			// Every example value is written as a string, including the dates: the format we want
			// people to copy is the text one, and a real date cell would display however their Excel
			// is configured.
			sample.createCell(i).setCellValue(i < example.size() ? example.get(i) : "");
			sheet.setColumnWidth(i, width(headers.get(i)));
		}
		sheet.createFreezePane(0, 1);
		return sheet;
	}

	/**
	 * Formats the columns that hold digit strings as text, so Excel stops "helpfully" turning a
	 * ten-digit phone number into 9.87654E+09 and a leading zero into nothing. The reader copes with
	 * either, but the admin's own screen should show what they typed.
	 */
	private static void asText(Workbook workbook, Sheet sheet, List<String> headers, List<String> textColumns) {
		CellStyle style = workbook.createCellStyle();
		style.setDataFormat(workbook.createDataFormat().getFormat("@"));
		for (String column : textColumns) {
			sheet.setDefaultColumnStyle(headers.indexOf(column), style);
		}
	}

	/** The second sheet: the prose, and the lookup block the dropdowns point at. */
	private static Sheet instructionsSheet(Workbook workbook, List<String> lines) {
		Sheet sheet = workbook.createSheet(INSTRUCTIONS);
		CellStyle titleStyle = headerStyle(workbook);
		for (int i = 0; i < lines.size(); i++) {
			Cell cell = sheet.createRow(i).createCell(0);
			cell.setCellValue(lines.get(i));
			if (i == 0) {
				cell.setCellStyle(titleStyle);
			}
		}
		sheet.setColumnWidth(0, 256 * 90);
		return sheet;
	}

	/**
	 * Writes one list of allowed values down a column of the Instructions sheet, under a heading.
	 *
	 * @param index position in the lookup block, zero-based; {@link #lookupRange} must be given the
	 *              same index for the dropdown to point at it
	 */
	private static void lookup(Sheet sheet, int index, String title, List<String> values) {
		int column = LOOKUP_FIRST_COLUMN + index;
		write(sheet, 0, column, title);
		for (int i = 0; i < values.size(); i++) {
			write(sheet, i + 1, column, values.get(i));
		}
		sheet.setColumnWidth(column, 256 * 24);
	}

	/**
	 * The absolute range of one lookup column, for a validation formula.
	 *
	 * @return null when the list is empty — a school with no classes yet gets a free-text column
	 *         rather than a dropdown that permits nothing at all
	 */
	private static String lookupRange(int index, int size) {
		if (size == 0) {
			return null;
		}
		String column = CellReference.convertNumToColString(LOOKUP_FIRST_COLUMN + index);
		return "%s!$%s$2:$%s$%d".formatted(INSTRUCTIONS, column, column, size + 1);
	}

	/** Attaches a dropdown to one column of the data sheet, for the rows people will actually fill in. */
	private static void dropdown(Sheet sheet, int column, String rangeFormula) {
		if (rangeFormula == null || column < 0) {
			return;
		}
		DataValidationHelper helper = sheet.getDataValidationHelper();
		DataValidationConstraint constraint = helper.createFormulaListConstraint(rangeFormula);
		DataValidation validation = helper.createValidation(constraint,
				new CellRangeAddressList(1, VALIDATED_ROWS, column, column));
		validation.setShowErrorBox(true);
		validation.setSuppressDropDownArrow(false);
		validation.createErrorBox("Not an allowed value", "Pick one of the values from the list.");
		sheet.addValidationData(validation);
	}

	// --- small helpers ----------------------------------------------------------------------------

	private static CellStyle headerStyle(Workbook workbook) {
		Font bold = workbook.createFont();
		bold.setBold(true);
		CellStyle style = workbook.createCellStyle();
		style.setFont(bold);
		style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		return style;
	}

	private static void write(Sheet sheet, int rowIndex, int column, String value) {
		Row row = sheet.getRow(rowIndex);
		if (row == null) {
			row = sheet.createRow(rowIndex);
		}
		row.createCell(column).setCellValue(value);
	}

	/** Wide enough for the header, and never cramped. Columns are in characters times 256. */
	private static int width(String header) {
		return 256 * Math.max(16, header.length() + 4);
	}

	/** Every section letter used anywhere in the school, in class order then alphabetical. */
	private static List<String> allSections(List<SchoolClass> schoolClasses) {
		Set<String> sections = new LinkedHashSet<>();
		for (SchoolClass schoolClass : schoolClasses) {
			if (schoolClass.getSections() != null) {
				sections.addAll(schoolClass.getSections());
			}
		}
		List<String> sorted = new ArrayList<>(sections);
		sorted.sort(String::compareTo);
		return sorted;
	}

	private static <E extends Enum<E>> List<String> names(E[] values) {
		return Arrays.stream(values).map(Enum::name).toList();
	}

	private static byte[] toBytes(Workbook workbook) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		workbook.write(out);
		return out.toByteArray();
	}
}

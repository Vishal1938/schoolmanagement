package com.school.exams.app;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.school.common.exceptions.AppException;
import com.school.common.exceptions.ErrorType;
import com.school.common.pdf.PdfColors;
import com.school.common.pdf.RemoteImageLoader;
import com.school.exams.api.ExamResult;
import com.school.exams.api.ResultOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Draws the report card: A4, one page for a normal subject list, branded from the school
 * configuration.
 *
 * <p>Pure layout. It is handed a {@link ReportCardData} and reads nothing else, so the question of
 * who may see a report card is settled before this class is reached.
 *
 * <p>Nothing here is school-specific. The name, address, logo and colours come from the
 * configuration, the footer is the configured report-card footer, and a school that has filled in
 * none of the optional fields still gets a correct, tidy page.
 */
@Component
public class ReportCardPdf {

	private static final Logger log = LoggerFactory.getLogger(ReportCardPdf.class);

	/** Half an inch all round. */
	private static final float MARGIN = 36f;

	/** Used when the theme has no usable primary colour. */
	private static final Color DEFAULT_BRAND = new Color(0x0B, 0x3D, 0x91);

	private static final Color INK = new Color(0x1F, 0x24, 0x28);
	private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
	private static final Color RULE = new Color(0xD1, 0xD5, 0xDB);
	private static final Color BAND = new Color(0xF4, 0xF5, 0xF7);

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

	/** Shown for a paper nobody entered a mark for, as against {@code AB} for a recorded absence. */
	private static final String UNMARKED = "—";

	private final RemoteImageLoader images;

	public ReportCardPdf(RemoteImageLoader images) {
		this.images = images;
	}

	public byte[] render(ReportCardData data) {
		Color brand = PdfColors.of(data.identity() == null || data.identity().theme() == null
				? null
				: data.identity().theme().primary(), DEFAULT_BRAND);
		ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
		Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
		try {
			PdfWriter.getInstance(document, out);
			document.addTitle("Report card — " + data.student().name() + " — " + data.result().examName());
			document.addCreator(schoolName(data));
			document.open();

			document.add(schoolHeader(data, brand));
			document.add(rule(brand, 2.5f, 10f));
			document.add(titleBand(data, brand));
			document.add(studentBlock(data));
			document.add(subjectTable(data, brand));
			document.add(summaryBlock(data, brand));
			document.add(attendanceLine(data));
			document.add(signatures(data));
			for (Paragraph paragraph : footer(data)) {
				document.add(paragraph);
			}

			document.close();
		}
		catch (DocumentException ex) {
			throw new AppException(ErrorType.INTERNAL_ERROR, "The report card could not be produced", ex);
		}
		return out.toByteArray();
	}

	// --- header -----------------------------------------------------------------------------------

	/**
	 * Logo, school name and address. Three columns with an empty one opposite the logo, so the name
	 * stays optically centred whether or not a logo is configured.
	 */
	private PdfPTable schoolHeader(ReportCardData data, Color brand) throws DocumentException {
		PdfPTable table = new PdfPTable(3);
		table.setWidthPercentage(100);
		table.setWidths(new float[] {14f, 72f, 14f});

		table.addCell(logoCell(data));

		ContactParts contact = ContactParts.of(data);
		PdfPCell middle = new PdfPCell();
		middle.setBorder(Rectangle.NO_BORDER);
		middle.setHorizontalAlignment(Element.ALIGN_CENTER);
		middle.addElement(centred(schoolName(data), font(15, Font.BOLD, brand)));
		String tagline = data.identity() == null ? null : data.identity().tagline();
		if (isPresent(tagline)) {
			middle.addElement(centred(tagline, font(8.5f, Font.ITALIC, MUTED)));
		}
		if (isPresent(contact.address())) {
			middle.addElement(centred(contact.address(), font(8.5f, Font.NORMAL, INK)));
		}
		if (isPresent(contact.contacts())) {
			middle.addElement(centred(contact.contacts(), font(8.5f, Font.NORMAL, MUTED)));
		}
		table.addCell(middle);

		table.addCell(blank());
		return table;
	}

	private PdfPCell logoCell(ReportCardData data) {
		PdfPCell cell = blank();
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		logo(data).ifPresent(logo -> {
			logo.scaleToFit(58f, 58f);
			cell.addElement(logo);
		});
		return cell;
	}

	private Optional<Image> logo(ReportCardData data) {
		String url = data.identity() == null ? null : data.identity().logoUrl();
		return images.load(url).flatMap(bytes -> {
			try {
				return Optional.of(Image.getInstance(bytes));
			}
			catch (DocumentException | IOException ex) {
				// Not an error worth failing a report card over: the bytes were not a readable image.
				log.warn("The configured logo could not be read as an image; the report card goes out without it");
				return Optional.empty();
			}
		});
	}

	/** The exam this card is for, in a band across the page. */
	private PdfPTable titleBand(ReportCardData data, Color brand) {
		PdfPTable table = new PdfPTable(1);
		table.setWidthPercentage(100);
		table.setSpacingAfter(10f);

		String session = isPresent(data.sessionName()) ? "  (" + data.sessionName() + ")" : "";
		PdfPCell cell = new PdfPCell(new Phrase("REPORT CARD — " + data.result().examName().toUpperCase(Locale.ROOT)
				+ session, font(11, Font.BOLD, Color.WHITE)));
		cell.setBackgroundColor(brand);
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setPadding(6f);
		table.addCell(cell);
		return table;
	}

	// --- the student ------------------------------------------------------------------------------

	private PdfPTable studentBlock(ReportCardData data) throws DocumentException {
		PdfPTable table = new PdfPTable(4);
		table.setWidthPercentage(100);
		table.setWidths(new float[] {22f, 28f, 22f, 28f});
		table.setSpacingAfter(12f);

		String section = data.student().section() == null ? "" : " — " + data.student().section();
		field(table, "Student", data.student().name());
		field(table, "Unique ID", data.student().uniqueId());
		field(table, "Class & section", orDash(data.className()) + section);
		field(table, "Roll no", String.valueOf(data.student().rollNo()));
		return table;
	}

	private void field(PdfPTable table, String label, String value) {
		PdfPCell key = new PdfPCell(new Phrase(label, font(8.5f, Font.BOLD, MUTED)));
		key.setBackgroundColor(BAND);
		style(key);
		table.addCell(key);

		PdfPCell cell = new PdfPCell(new Phrase(orDash(value), font(10, Font.NORMAL, INK)));
		style(cell);
		table.addCell(cell);
	}

	// --- the marks --------------------------------------------------------------------------------

	private PdfPTable subjectTable(ReportCardData data, Color brand) throws DocumentException {
		PdfPTable table = new PdfPTable(5);
		table.setWidthPercentage(100);
		table.setWidths(new float[] {44f, 14f, 14f, 14f, 14f});
		table.setHeaderRows(1);
		table.setSpacingAfter(10f);

		headerCell(table, "Subject", brand, Element.ALIGN_LEFT);
		headerCell(table, "Marks", brand, Element.ALIGN_CENTER);
		headerCell(table, "Max", brand, Element.ALIGN_CENTER);
		headerCell(table, "Grade", brand, Element.ALIGN_CENTER);
		headerCell(table, "Result", brand, Element.ALIGN_CENTER);

		for (ExamResult.SubjectResult subject : data.result().subjects()) {
			body(table, orDash(subject.subjectName()), Element.ALIGN_LEFT, Font.NORMAL);
			body(table, marks(subject), Element.ALIGN_CENTER, Font.NORMAL);
			body(table, String.valueOf(subject.maxMarks()), Element.ALIGN_CENTER, Font.NORMAL);
			body(table, orDash(subject.grade()), Element.ALIGN_CENTER, Font.NORMAL);
			body(table, subjectOutcome(subject), Element.ALIGN_CENTER, Font.NORMAL);
		}

		totalCell(table, "Total", Element.ALIGN_LEFT);
		totalCell(table, String.valueOf(data.result().total()), Element.ALIGN_CENTER);
		totalCell(table, String.valueOf(data.result().maxTotal()), Element.ALIGN_CENTER);
		totalCell(table, orDash(data.result().grade()), Element.ALIGN_CENTER);
		totalCell(table, data.result().result() == null ? UNMARKED : label(data.result().result()),
				Element.ALIGN_CENTER);
		return table;
	}

	/** {@code AB} for a recorded absence, an em dash for a paper nobody marked. */
	private static String marks(ExamResult.SubjectResult subject) {
		if (subject.absent()) {
			return "AB";
		}
		return subject.marks() == null ? UNMARKED : String.valueOf(subject.marks());
	}

	/**
	 * An absence is a recorded outcome rather than a gap, so it reads as a fail; only a paper nobody
	 * entered at all is left blank.
	 */
	private static String subjectOutcome(ExamResult.SubjectResult subject) {
		if (!subject.absent() && subject.marks() == null) {
			return UNMARKED;
		}
		return subject.pass() ? "Pass" : "Fail";
	}

	// --- the totals -------------------------------------------------------------------------------

	/** Percentage, grade, rank and result, as four labelled figures across the page. */
	private PdfPTable summaryBlock(ReportCardData data, Color brand) throws DocumentException {
		ExamResult result = data.result();
		PdfPTable table = new PdfPTable(4);
		table.setWidthPercentage(100);
		table.setWidths(new float[] {25f, 25f, 25f, 25f});
		table.setSpacingAfter(4f);

		figure(table, "Percentage", percentage(result.percentage()), brand);
		figure(table, "Grade", orDash(result.grade()), brand);
		figure(table, "Rank in section", rank(result), brand);
		figure(table, "Result", result.result() == null ? UNMARKED : label(result.result()), brand);
		return table;
	}

	private void figure(PdfPTable table, String label, String value, Color brand) {
		PdfPCell cell = new PdfPCell();
		cell.setBorder(Rectangle.BOX);
		cell.setBorderColor(RULE);
		cell.setPadding(6f);
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.addElement(centred(label.toUpperCase(Locale.ROOT), font(7.5f, Font.BOLD, MUTED)));
		cell.addElement(centred(value, font(13, Font.BOLD, brand)));
		table.addCell(cell);
	}

	/**
	 * Attendance across the session.
	 *
	 * <p>The grading scheme's band remark is deliberately <em>not</em> printed next to it. The remark
	 * describes the percentage band — "Pass" for C1 — and a student who failed one paper has a C1 band
	 * and a FAIL result at the same time, so the two side by side would contradict each other on the
	 * one document a parent keeps.
	 */
	private Paragraph attendanceLine(ReportCardData data) {
		String attendance = data.attendance() == null || data.attendance().markedDays() == 0
				? "no attendance has been marked for this session yet"
				: percentage(data.attendance().percentage()) + " of " + data.attendance().markedDays()
						+ " marked days";
		Paragraph paragraph = new Paragraph("Attendance for the session: " + attendance, font(9, Font.NORMAL, INK));
		paragraph.setSpacingBefore(8f);
		return paragraph;
	}

	// --- signatures and footer --------------------------------------------------------------------

	/**
	 * Two lines to sign, with the name of whoever is expected to sign printed under the rule rather
	 * than on it, so the rule stays clear for an actual signature.
	 */
	private PdfPTable signatures(ReportCardData data) throws DocumentException {
		PdfPTable table = new PdfPTable(3);
		table.setWidthPercentage(100);
		table.setWidths(new float[] {38f, 24f, 38f});
		table.setSpacingBefore(34f);

		table.addCell(signatureSpace());
		table.addCell(blank());
		table.addCell(signatureSpace());

		table.addCell(signatureLabel("Class Teacher", data.classTeacherName()));
		table.addCell(blank());
		table.addCell(signatureLabel(isPresent(data.principalDesignation()) ? data.principalDesignation() : "Principal",
				data.principalName()));
		return table;
	}

	/** The empty space above the rule; the rule itself is the top border of the label cell. */
	private PdfPCell signatureSpace() {
		PdfPCell cell = blank();
		cell.setFixedHeight(30f);
		return cell;
	}

	private PdfPCell signatureLabel(String role, String name) {
		PdfPCell cell = new PdfPCell();
		cell.setBorder(Rectangle.TOP);
		cell.setBorderColor(INK);
		cell.setPaddingTop(3f);
		cell.addElement(centred(role, font(9, Font.BOLD, INK)));
		if (isPresent(name)) {
			cell.addElement(centred(name, font(8, Font.NORMAL, MUTED)));
		}
		return cell;
	}

	private List<Paragraph> footer(ReportCardData data) {
		Paragraph generated = centred("Generated on " + DAY.format(data.generatedOn()), font(7.5f, Font.NORMAL, MUTED));
		generated.setSpacingBefore(18f);
		if (!isPresent(data.footer())) {
			return List.of(generated);
		}
		Paragraph footer = centred(data.footer(), font(7.5f, Font.ITALIC, MUTED));
		footer.setSpacingBefore(6f);
		return List.of(generated, footer);
	}

	// --- small builders ---------------------------------------------------------------------------

	private void headerCell(PdfPTable table, String text, Color brand, int alignment) {
		PdfPCell cell = new PdfPCell(new Phrase(text, font(9, Font.BOLD, Color.WHITE)));
		cell.setBackgroundColor(brand);
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setHorizontalAlignment(alignment);
		cell.setPadding(5f);
		table.addCell(cell);
	}

	private void body(PdfPTable table, String text, int alignment, int style) {
		PdfPCell cell = new PdfPCell(new Phrase(text, font(9.5f, style, INK)));
		cell.setHorizontalAlignment(alignment);
		style(cell);
		table.addCell(cell);
	}

	private void totalCell(PdfPTable table, String text, int alignment) {
		PdfPCell cell = new PdfPCell(new Phrase(text, font(9.5f, Font.BOLD, INK)));
		cell.setBackgroundColor(BAND);
		cell.setHorizontalAlignment(alignment);
		style(cell);
		table.addCell(cell);
	}

	/** A horizontal rule, drawn as a thin filled cell. */
	private PdfPTable rule(Color colour, float thickness, float spacingAfter) {
		PdfPTable table = new PdfPTable(1);
		table.setWidthPercentage(100);
		table.setSpacingBefore(4f);
		table.setSpacingAfter(spacingAfter);
		PdfPCell cell = new PdfPCell();
		cell.setFixedHeight(thickness);
		cell.setBackgroundColor(colour);
		cell.setBorder(Rectangle.NO_BORDER);
		table.addCell(cell);
		return table;
	}

	private static void style(PdfPCell cell) {
		cell.setBorder(Rectangle.BOX);
		cell.setBorderColor(RULE);
		cell.setPadding(5f);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
	}

	private static PdfPCell blank() {
		PdfPCell cell = new PdfPCell();
		cell.setBorder(Rectangle.NO_BORDER);
		return cell;
	}

	private static Paragraph centred(String text, Font font) {
		Paragraph paragraph = new Paragraph(text, font);
		paragraph.setAlignment(Element.ALIGN_CENTER);
		return paragraph;
	}

	private static Font font(float size, int style, Color colour) {
		return new Font(Font.HELVETICA, size, style, colour);
	}

	// --- text -------------------------------------------------------------------------------------

	private static String schoolName(ReportCardData data) {
		return data.identity() == null || !isPresent(data.identity().name()) ? "School" : data.identity().name();
	}

	private static String rank(ExamResult result) {
		return result.rank() <= 0 ? UNMARKED : result.rank() + " of " + result.rankOutOf();
	}

	private static String percentage(double value) {
		return String.format(Locale.ENGLISH, "%.2f%%", value);
	}

	private static String label(ResultOutcome outcome) {
		return outcome == ResultOutcome.PASS ? "PASS" : "FAIL";
	}

	private static String orDash(String value) {
		return isPresent(value) ? value : UNMARKED;
	}

	private static boolean isPresent(String value) {
		return value != null && !value.isBlank();
	}

	/** Joins the configured contact details into the two lines the header prints. */
	private record ContactParts(String address, String contacts) {

		static ContactParts of(ReportCardData data) {
			if (data.contact() == null) {
				return new ContactParts("", "");
			}
			String address = join(" · ",
					join(", ", data.contact().addressLine1(), data.contact().addressLine2()),
					join(", ", data.contact().city(), data.contact().state()),
					data.contact().postalCode());
			String contacts = join(" · ", data.contact().phone(), data.contact().email(),
					data.contact().websiteUrl());
			return new ContactParts(address, contacts);
		}

		private static String join(String separator, String... values) {
			StringBuilder joined = new StringBuilder();
			for (String value : values) {
				if (isPresent(value)) {
					if (!joined.isEmpty()) {
						joined.append(separator);
					}
					joined.append(value.trim());
				}
			}
			return joined.toString();
		}
	}
}

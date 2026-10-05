package com.school.payroll.app;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import com.school.common.pdf.AmountInWords;
import com.school.common.pdf.PdfColors;
import com.school.common.pdf.RemoteImageLoader;
import com.school.payroll.domain.AppliedDeduction;
import com.school.payroll.domain.DeductionType;
import com.school.payroll.domain.PayrollRecord;
import com.school.payroll.domain.PayrollStatus;
import com.school.payroll.domain.SalaryComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Draws the salary slip: A4, one page, branded from the school configuration.
 *
 * <p>Pure layout, the same arrangement as the receipt in B12 and the report card in B9. Nothing here
 * is school-specific — the name, address, logo, colours and footer all come from
 * {@code school_config}, and a school that has filled in none of the optional fields still gets a
 * correct, tidy page.
 *
 * <p>Earnings and deductions are listed in full rather than netted off, and the net pay is printed
 * twice, in figures and in words. A slip is what somebody takes to a bank for a loan: every figure
 * on it has to be traceable to a line above it.
 */
@Component
public class SalarySlipPdf {

	private static final Logger log = LoggerFactory.getLogger(SalarySlipPdf.class);

	/** Half an inch all round. */
	private static final float MARGIN = 36f;

	/** Used when the theme has no usable primary colour. */
	private static final Color DEFAULT_BRAND = new Color(0x0B, 0x3D, 0x91);

	private static final Color INK = new Color(0x1F, 0x24, 0x28);
	private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
	private static final Color RULE = new Color(0xD1, 0xD5, 0xDB);
	private static final Color BAND = new Color(0xF4, 0xF5, 0xF7);

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

	private static final String DASH = "—";

	private final RemoteImageLoader images;

	public SalarySlipPdf(RemoteImageLoader images) {
		this.images = images;
	}

	public byte[] render(SalarySlipData data) {
		Color brand = PdfColors.of(data.identity() == null || data.identity().theme() == null
				? null
				: data.identity().theme().primary(), DEFAULT_BRAND);
		ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
		Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
		try {
			PdfWriter.getInstance(document, out);
			document.addTitle("Salary slip " + data.monthLabel() + " " + data.record().getEmployeeUniqueId());
			document.addCreator(schoolName(data));
			document.open();

			document.add(schoolHeader(data, brand));
			document.add(rule(brand, 2.5f, 10f));
			document.add(titleBand(data, brand));
			document.add(employeeBlock(data));
			document.add(earningsTable(data, brand));
			document.add(deductionsTable(data, brand));
			document.add(netPayBand(data, brand));
			document.add(netInWords(data));
			document.add(paymentBlock(data));
			for (Paragraph paragraph : footer(data)) {
				document.add(paragraph);
			}

			document.close();
		}
		catch (DocumentException ex) {
			throw new AppException(ErrorType.INTERNAL_ERROR, "The salary slip could not be produced", ex);
		}
		return out.toByteArray();
	}

	// --- header -----------------------------------------------------------------------------------

	/**
	 * Logo, school name and address. Three columns with an empty one opposite the logo, so the name
	 * stays optically centred whether or not a logo is configured.
	 */
	private PdfPTable schoolHeader(SalarySlipData data, Color brand) throws DocumentException {
		PdfPTable table = new PdfPTable(3);
		table.setWidthPercentage(100);
		table.setWidths(new float[] {14f, 72f, 14f});

		table.addCell(logoCell(data));

		ContactParts contact = ContactParts.of(data);
		PdfPCell middle = blank();
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

	private PdfPCell logoCell(SalarySlipData data) {
		PdfPCell cell = blank();
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		logo(data).ifPresent(logo -> {
			logo.scaleToFit(58f, 58f);
			cell.addElement(logo);
		});
		return cell;
	}

	private Optional<Image> logo(SalarySlipData data) {
		String url = data.identity() == null ? null : data.identity().logoUrl();
		return images.load(url).flatMap(bytes -> {
			try {
				return Optional.of(Image.getInstance(bytes));
			}
			catch (DocumentException | IOException ex) {
				// Not worth failing a slip over: the bytes were not a readable image.
				log.warn("The configured logo could not be read as an image; the salary slip goes out without it");
				return Optional.empty();
			}
		});
	}

	/** "SALARY SLIP — OCTOBER 2026": the month belongs in the title, since that is what identifies it. */
	private PdfPTable titleBand(SalarySlipData data, Color brand) {
		PdfPTable table = new PdfPTable(1);
		table.setWidthPercentage(100);
		table.setSpacingAfter(10f);

		String title = "SALARY SLIP — " + data.monthLabel().toUpperCase(Locale.ENGLISH);
		PdfPCell cell = new PdfPCell(new Phrase(title, font(11, Font.BOLD, Color.WHITE)));
		cell.setBackgroundColor(brand);
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setPadding(6f);
		table.addCell(cell);
		return table;
	}

	// --- who --------------------------------------------------------------------------------------

	private PdfPTable employeeBlock(SalarySlipData data) throws DocumentException {
		PdfPTable table = new PdfPTable(4);
		table.setWidthPercentage(100);
		table.setWidths(new float[] {22f, 28f, 22f, 28f});
		table.setSpacingAfter(12f);

		PayrollRecord record = data.record();
		field(table, "Employee", record.getEmployeeName());
		field(table, "Unique ID", record.getEmployeeUniqueId());
		// A teacher has no designation; their type is the honest answer in that column.
		field(table, "Designation", isPresent(record.getDesignation())
				? record.getDesignation()
				: titleCase(record.getEmployeeType()));
		field(table, "Month", data.monthLabel());
		return table;
	}

	// --- the figures ------------------------------------------------------------------------------

	/** Basic and every allowance, down to the gross. */
	private PdfPTable earningsTable(SalarySlipData data, Color brand) throws DocumentException {
		PdfPTable table = new PdfPTable(2);
		table.setWidthPercentage(100);
		table.setWidths(new float[] {70f, 30f});
		table.setSpacingAfter(10f);

		headerCell(table, "Earnings", brand, Element.ALIGN_LEFT);
		headerCell(table, "Amount", brand, Element.ALIGN_RIGHT);

		PayrollRecord record = data.record();
		body(table, "Basic", Element.ALIGN_LEFT, Font.NORMAL);
		body(table, money(record.getBasic()), Element.ALIGN_RIGHT, Font.NORMAL);
		for (SalaryComponent allowance : record.getAllowances() == null
				? List.<SalaryComponent>of()
				: record.getAllowances()) {
			body(table, orDash(allowance.name()), Element.ALIGN_LEFT, Font.NORMAL);
			body(table, money(allowance.amount()), Element.ALIGN_RIGHT, Font.NORMAL);
		}

		totalCell(table, "Gross earnings", Element.ALIGN_LEFT);
		totalCell(table, money(record.getGross()), Element.ALIGN_RIGHT);
		return table;
	}

	/**
	 * Every deduction, then the advance installment and any loss of pay.
	 *
	 * <p>The last two are deductions in every sense that matters to the person reading the slip, and
	 * putting them in the same table is what makes the net reconcile by reading down the page.
	 */
	private PdfPTable deductionsTable(SalarySlipData data, Color brand) throws DocumentException {
		PdfPTable table = new PdfPTable(2);
		table.setWidthPercentage(100);
		table.setWidths(new float[] {70f, 30f});
		table.setSpacingAfter(10f);

		headerCell(table, "Deductions", brand, Element.ALIGN_LEFT);
		headerCell(table, "Amount", brand, Element.ALIGN_RIGHT);

		PayrollRecord record = data.record();
		boolean any = false;
		for (AppliedDeduction deduction : record.getDeductions() == null
				? List.<AppliedDeduction>of()
				: record.getDeductions()) {
			body(table, label(deduction), Element.ALIGN_LEFT, Font.NORMAL);
			body(table, money(deduction.amount()), Element.ALIGN_RIGHT, Font.NORMAL);
			any = true;
		}
		if (record.getLossOfPay() > 0L) {
			body(table, "Loss of pay — " + absenceDetail(record), Element.ALIGN_LEFT, Font.NORMAL);
			body(table, money(record.getLossOfPay()), Element.ALIGN_RIGHT, Font.NORMAL);
			any = true;
		}
		if (record.getAdvanceRecovery() > 0L) {
			body(table, "Advance recovery", Element.ALIGN_LEFT, Font.NORMAL);
			body(table, money(record.getAdvanceRecovery()), Element.ALIGN_RIGHT, Font.NORMAL);
			any = true;
		}
		if (!any) {
			// An empty table reads as a rendering fault; "nothing was deducted" is a statement.
			body(table, "No deductions", Element.ALIGN_LEFT, Font.ITALIC, MUTED);
			body(table, money(0L), Element.ALIGN_RIGHT, Font.NORMAL, MUTED);
		}

		long total = record.getDeductionTotal() + record.getLossOfPay() + record.getAdvanceRecovery();
		totalCell(table, "Total deductions", Element.ALIGN_LEFT);
		totalCell(table, money(total), Element.ALIGN_RIGHT);
		return table;
	}

	private PdfPTable netPayBand(SalarySlipData data, Color brand) throws DocumentException {
		PdfPTable table = new PdfPTable(2);
		table.setWidthPercentage(100);
		table.setWidths(new float[] {70f, 30f});
		table.setSpacingAfter(4f);

		PdfPCell label = new PdfPCell(new Phrase("NET PAY", font(11, Font.BOLD, Color.WHITE)));
		label.setBackgroundColor(brand);
		label.setBorder(Rectangle.NO_BORDER);
		label.setPadding(6f);
		table.addCell(label);

		PdfPCell amount = new PdfPCell(new Phrase(money(data.record().getNet()), font(11, Font.BOLD, Color.WHITE)));
		amount.setBackgroundColor(brand);
		amount.setBorder(Rectangle.NO_BORDER);
		amount.setHorizontalAlignment(Element.ALIGN_RIGHT);
		amount.setPadding(6f);
		table.addCell(amount);
		return table;
	}

	/** The figure again, in words — the thing that makes the paper hard to alter. */
	private Paragraph netInWords(SalarySlipData data) {
		Paragraph paragraph = new Paragraph(AmountInWords.rupees(data.record().getNet()),
				font(9.5f, Font.BOLD, INK));
		paragraph.setSpacingBefore(6f);
		paragraph.setSpacingAfter(12f);
		return paragraph;
	}

	/** Whether this salary has actually gone out, and how. The reason anyone asks for the PDF twice. */
	private PdfPTable paymentBlock(SalarySlipData data) throws DocumentException {
		PdfPTable table = new PdfPTable(4);
		table.setWidthPercentage(100);
		table.setWidths(new float[] {22f, 28f, 22f, 28f});

		PayrollRecord record = data.record();
		field(table, "Status", record.getStatus() == PayrollStatus.PAID ? "PAID" : "PENDING");
		field(table, "Paid on", record.getPaidOn() == null ? DASH : DAY.format(record.getPaidOn()));
		field(table, "Mode", record.getMode() == null ? DASH : record.getMode().name());
		field(table, "Reference", record.getReference());
		return table;
	}

	private List<Paragraph> footer(SalarySlipData data) {
		Paragraph note = centred("This is a computer-generated salary slip.", font(7.5f, Font.NORMAL, MUTED));
		note.setSpacingBefore(24f);
		if (!isPresent(data.footer())) {
			return List.of(note);
		}
		Paragraph footer = centred(data.footer(), font(7.5f, Font.ITALIC, MUTED));
		footer.setSpacingBefore(6f);
		return List.of(note, footer);
	}

	// --- small builders ---------------------------------------------------------------------------

	private void field(PdfPTable table, String label, String value) {
		PdfPCell key = new PdfPCell(new Phrase(label, font(8.5f, Font.BOLD, MUTED)));
		key.setBackgroundColor(BAND);
		style(key);
		table.addCell(key);

		PdfPCell cell = new PdfPCell(new Phrase(orDash(value), font(10, Font.NORMAL, INK)));
		style(cell);
		table.addCell(cell);
	}

	private void headerCell(PdfPTable table, String text, Color brand, int alignment) {
		PdfPCell cell = new PdfPCell(new Phrase(text, font(9, Font.BOLD, Color.WHITE)));
		cell.setBackgroundColor(brand);
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setHorizontalAlignment(alignment);
		cell.setPadding(5f);
		table.addCell(cell);
	}

	private void body(PdfPTable table, String text, int alignment, int style) {
		body(table, text, alignment, style, INK);
	}

	private void body(PdfPTable table, String text, int alignment, int style, Color colour) {
		PdfPCell cell = new PdfPCell(new Phrase(text, font(9.5f, style, colour)));
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

	/** "PF (12% of basic)", so a figure on the slip can be checked without the structure to hand. */
	private static String label(AppliedDeduction deduction) {
		if (deduction.type() != DeductionType.PERCENT_OF_BASIC) {
			return orDash(deduction.name());
		}
		return orDash(deduction.name()) + " (" + percent(deduction.value()) + " of basic)";
	}

	/** Basis points as a percentage, without trailing zeros: 1200 reads "12%", 75 reads "0.75%". */
	private static String percent(long basisPoints) {
		return BigDecimal.valueOf(basisPoints, 2).stripTrailingZeros().toPlainString() + "%";
	}

	/** "3 absent, 1 half day of 31 days", which is what makes the loss-of-pay figure checkable. */
	private static String absenceDetail(PayrollRecord record) {
		List<String> parts = new ArrayList<>();
		if (record.getAbsentDays() > 0) {
			parts.add(record.getAbsentDays() + " absent");
		}
		if (record.getHalfDays() > 0) {
			parts.add(record.getHalfDays() + (record.getHalfDays() == 1 ? " half day" : " half days"));
		}
		return String.join(", ", parts) + " of " + record.getDaysInMonth() + " days";
	}

	/** Paise to rupees for display only. Everything stored and returned by the API stays paise. */
	private static String money(long paise) {
		return String.format(Locale.ENGLISH, "%,.2f", paise / 100.0d);
	}

	/** TEACHER reads as Teacher on a document somebody hands to their bank. */
	private static String titleCase(String value) {
		if (!isPresent(value)) {
			return null;
		}
		String lower = value.trim().toLowerCase(Locale.ENGLISH);
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private static String schoolName(SalarySlipData data) {
		return data.identity() == null || !isPresent(data.identity().name()) ? "School" : data.identity().name();
	}

	private static String orDash(String value) {
		return isPresent(value) ? value : DASH;
	}

	private static boolean isPresent(String value) {
		return value != null && !value.isBlank();
	}

	/** Joins the configured contact details into the two lines the header prints. */
	private record ContactParts(String address, String contacts) {

		static ContactParts of(SalarySlipData data) {
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

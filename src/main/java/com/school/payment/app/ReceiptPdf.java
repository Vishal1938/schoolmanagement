package com.school.payment.app;

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
import com.school.common.pdf.AmountInWords;
import com.school.common.pdf.PdfColors;
import com.school.common.pdf.RemoteImageLoader;
import com.school.payment.domain.HeadAllocation;
import com.school.payment.domain.Payment;
import com.school.payment.domain.PaymentAllocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Draws the fee receipt: A4, one page, branded from the school configuration.
 *
 * <p>Pure layout, the same arrangement as the report card in B9. Nothing here is school-specific —
 * the name, address, logo, colours and footer all come from {@code school_config}, and a school that
 * has filled in none of the optional fields still gets a correct, tidy page.
 *
 * <p>The amount is printed twice, in figures and in words, which is what makes a receipt hard to
 * alter after it has been handed over.
 */
@Component
public class ReceiptPdf {

	private static final Logger log = LoggerFactory.getLogger(ReceiptPdf.class);

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

	public ReceiptPdf(RemoteImageLoader images) {
		this.images = images;
	}

	public byte[] render(ReceiptData data) {
		Color brand = PdfColors.of(data.identity() == null || data.identity().theme() == null
				? null
				: data.identity().theme().primary(), DEFAULT_BRAND);
		ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
		Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
		try {
			PdfWriter.getInstance(document, out);
			document.addTitle("Fee receipt " + orDash(data.payment().getReceiptNo()));
			document.addCreator(schoolName(data));
			document.open();

			document.add(schoolHeader(data, brand));
			document.add(rule(brand, 2.5f, 10f));
			document.add(titleBand(brand));
			document.add(receiptBlock(data));
			document.add(allocationTable(data, brand));
			document.add(amountInWords(data));
			document.add(paymentBlock(data));
			document.add(paidBy(data));
			for (Paragraph paragraph : footer(data)) {
				document.add(paragraph);
			}

			document.close();
		}
		catch (DocumentException ex) {
			throw new AppException(ErrorType.INTERNAL_ERROR, "The receipt could not be produced", ex);
		}
		return out.toByteArray();
	}

	// --- header -----------------------------------------------------------------------------------

	/**
	 * Logo, school name and address. Three columns with an empty one opposite the logo, so the name
	 * stays optically centred whether or not a logo is configured.
	 */
	private PdfPTable schoolHeader(ReceiptData data, Color brand) throws DocumentException {
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

	private PdfPCell logoCell(ReceiptData data) {
		PdfPCell cell = blank();
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		logo(data).ifPresent(logo -> {
			logo.scaleToFit(58f, 58f);
			cell.addElement(logo);
		});
		return cell;
	}

	private Optional<Image> logo(ReceiptData data) {
		String url = data.identity() == null ? null : data.identity().logoUrl();
		return images.load(url).flatMap(bytes -> {
			try {
				return Optional.of(Image.getInstance(bytes));
			}
			catch (DocumentException | IOException ex) {
				// Not worth failing a receipt over: the bytes were not a readable image.
				log.warn("The configured logo could not be read as an image; the receipt goes out without it");
				return Optional.empty();
			}
		});
	}

	private PdfPTable titleBand(Color brand) {
		PdfPTable table = new PdfPTable(1);
		table.setWidthPercentage(100);
		table.setSpacingAfter(10f);

		PdfPCell cell = new PdfPCell(new Phrase("FEE RECEIPT", font(11, Font.BOLD, Color.WHITE)));
		cell.setBackgroundColor(brand);
		cell.setBorder(Rectangle.NO_BORDER);
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setPadding(6f);
		table.addCell(cell);
		return table;
	}

	// --- who and what -----------------------------------------------------------------------------

	private PdfPTable receiptBlock(ReceiptData data) throws DocumentException {
		PdfPTable table = new PdfPTable(4);
		table.setWidthPercentage(100);
		table.setWidths(new float[] {22f, 28f, 22f, 28f});
		table.setSpacingAfter(12f);

		String section = isPresent(data.section()) ? " — " + data.section() : "";
		field(table, "Receipt no", data.payment().getReceiptNo());
		field(table, "Date", data.paidOn() == null ? DASH : DAY.format(data.paidOn()));
		field(table, "Student", data.studentName());
		field(table, "Unique ID", data.payment().getStudentUniqueId());
		field(table, "Class & section", orDash(data.className()) + section);
		field(table, "Mode", data.payment().getMode() == null ? DASH : data.payment().getMode().name());
		return table;
	}

	/**
	 * One row per invoice paid, with the heads under it.
	 *
	 * <p>The heads are listed because a family wants to know what the money went on, and the figure
	 * against each is the share of <em>this</em> payment it covered — not what the head costs. A part
	 * payment therefore shows part of each head rather than some heads paid and others untouched.
	 */
	private PdfPTable allocationTable(ReceiptData data, Color brand) throws DocumentException {
		PdfPTable table = new PdfPTable(4);
		table.setWidthPercentage(100);
		table.setWidths(new float[] {40f, 20f, 20f, 20f});
		table.setHeaderRows(1);
		table.setSpacingAfter(10f);

		headerCell(table, "Installment / head", brand, Element.ALIGN_LEFT);
		headerCell(table, "Due date", brand, Element.ALIGN_CENTER);
		headerCell(table, "Late fine", brand, Element.ALIGN_RIGHT);
		headerCell(table, "Amount", brand, Element.ALIGN_RIGHT);

		Payment payment = data.payment();
		List<PaymentAllocation> allocations = payment.getAllocations() == null
				? List.of()
				: payment.getAllocations();
		for (PaymentAllocation allocation : allocations) {
			body(table, orDash(allocation.installmentName()), Element.ALIGN_LEFT, Font.BOLD);
			body(table, allocation.dueDate() == null ? DASH : DAY.format(allocation.dueDate()),
					Element.ALIGN_CENTER, Font.NORMAL);
			body(table, money(allocation.lateFine()), Element.ALIGN_RIGHT, Font.NORMAL);
			body(table, money(allocation.amount()), Element.ALIGN_RIGHT, Font.NORMAL);

			for (HeadAllocation head : allocation.heads() == null ? List.<HeadAllocation>of() : allocation.heads()) {
				body(table, "    " + orDash(head.headName()), Element.ALIGN_LEFT, Font.NORMAL, MUTED);
				body(table, "", Element.ALIGN_CENTER, Font.NORMAL, MUTED);
				body(table, "", Element.ALIGN_RIGHT, Font.NORMAL, MUTED);
				body(table, money(head.amount()), Element.ALIGN_RIGHT, Font.NORMAL, MUTED);
			}
		}

		totalCell(table, "Total received", Element.ALIGN_LEFT);
		totalCell(table, "", Element.ALIGN_CENTER);
		totalCell(table, money(payment.lateFineTotal()), Element.ALIGN_RIGHT);
		totalCell(table, money(payment.getAmount()), Element.ALIGN_RIGHT);
		return table;
	}

	/** The figure again, in words — the thing that makes the paper hard to alter. */
	private Paragraph amountInWords(ReceiptData data) {
		Paragraph paragraph = new Paragraph(AmountInWords.rupees(data.payment().getAmount()),
				font(9.5f, Font.BOLD, INK));
		paragraph.setSpacingAfter(10f);
		return paragraph;
	}

	private PdfPTable paymentBlock(ReceiptData data) throws DocumentException {
		PdfPTable table = new PdfPTable(4);
		table.setWidthPercentage(100);
		table.setWidths(new float[] {22f, 28f, 22f, 28f});
		table.setSpacingAfter(4f);

		field(table, "Reference", data.payment().getReference());
		field(table, "Fee paid", money(data.payment().principalTotal()));
		field(table, "Late fine paid", money(data.payment().lateFineTotal()));
		field(table, "Total", money(data.payment().getAmount()));
		return table;
	}

	/**
	 * Who actually handed the money over. There are no parent accounts, so this line is the only
	 * record of it — and it is the one thing on the receipt a family checks.
	 */
	private Paragraph paidBy(ReceiptData data) {
		String relation = data.payment().getPayerRelation() == null
				? DASH
				: data.payment().getPayerRelation().name();
		Paragraph paragraph = new Paragraph("Paid by: " + orDash(data.payment().getPayerName())
				+ " (" + relation + ")", font(9.5f, Font.NORMAL, INK));
		paragraph.setSpacingBefore(10f);
		return paragraph;
	}

	private List<Paragraph> footer(ReceiptData data) {
		Paragraph note = centred("This is a computer-generated receipt.", font(7.5f, Font.NORMAL, MUTED));
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

	/** Paise to rupees for display only. Everything stored and returned by the API stays paise. */
	private static String money(long paise) {
		return String.format(Locale.ENGLISH, "%,.2f", paise / 100.0d);
	}

	private static String schoolName(ReceiptData data) {
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

		static ContactParts of(ReceiptData data) {
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

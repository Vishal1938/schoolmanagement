package com.school.people.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.school.common.exceptions.AppException;
import com.school.common.exceptions.ErrorType;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ValidationException;
import com.school.common.storage.ObjectStorage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * The credentials workbook an import leaves behind: who was created, and the one-time password each
 * of them was given.
 *
 * <p>This file is the only place those passwords exist in readable form. They are never in the
 * import response, never in the audit trail and never in a log line — the same rule the single-create
 * endpoints follow, except that a hundred passwords cannot be shown on screen, so they go into a
 * spreadsheet instead.
 *
 * <p>Which makes the file a liability, and it is treated as one. It is stored under
 * {@code credentials/}, outside the publicly readable prefix, so there is no URL that reaches it
 * without going through {@code GET /imports/credentials/{id}} and the permission on it. The id is a
 * random UUID rather than anything derived from the import, so one cannot be guessed from another.
 * And it is <strong>deleted after {@value #RETENTION_HOURS} hours</strong> by the sweep below: the
 * admin who ran the import needs it that afternoon, and nobody needs it next month.
 */
@Service
public class ImportCredentialsService {

	/** Deliberately not under {@code app.storage.public-prefix}. */
	static final String PREFIX = "credentials/";

	static final int RETENTION_HOURS = 24;

	/** Also what the two template endpoints serve, which is why it is public. */
	public static final String XLSX_CONTENT_TYPE =
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	/** The shape of an id this service issues: a bare UUID, so {@code PREFIX + id} cannot escape it. */
	private static final Pattern ID = Pattern.compile("^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$");

	private static final Duration RETENTION = Duration.ofHours(RETENTION_HOURS);

	private static final Logger log = LoggerFactory.getLogger(ImportCredentialsService.class);

	private final ObjectStorage storage;
	private final Clock clock;

	public ImportCredentialsService(ObjectStorage storage, Clock clock) {
		this.storage = storage;
		this.clock = clock;
	}

	/**
	 * Renders the credentials and stores them.
	 *
	 * @return the id to download them by, or null when the import created no logins at all — an
	 *         employee file of staff with {@code Can Login = NO} produces nothing worth keeping
	 */
	public String store(List<Credential> credentials) {
		if (credentials.isEmpty()) {
			return null;
		}
		String id = UUID.randomUUID().toString();
		// No user metadata: it travels in HTTP headers and there is nothing about this object that is
		// safe to put there.
		storage.put(PREFIX + id + ".xlsx", render(credentials), XLSX_CONTENT_TYPE, Map.of());
		log.info("Stored credentials for {} new login(s) under import file {}", credentials.size(), id);
		return id;
	}

	/** The bytes of one credentials file. */
	public Download download(String id) {
		if (id == null || !ID.matcher(id).matches()) {
			// Not a 404: the id is malformed, so there is nothing to go looking for.
			throw new ValidationException("That is not a credentials file id",
					List.of(new FieldViolation("id", "must be an id returned by an import")));
		}
		ObjectStorage.StoredObject object = storage.get(PREFIX + id + ".xlsx");
		return new Download(object.bytes(), "credentials-" + id + ".xlsx");
	}

	/**
	 * Deletes credentials files older than the retention window.
	 *
	 * <p>Hourly rather than daily, so a file lives for at most 25 hours instead of up to 48. One
	 * failed delete does not stop the sweep: the next pass will try it again, and a file left an hour
	 * longer is better than a pass that gives up on the rest of the prefix.
	 */
	@Scheduled(initialDelay = 5, fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
	public void deleteExpired() {
		Instant cutoff = Instant.now(clock).minus(RETENTION);
		List<ObjectStorage.StoredObjectSummary> expired;
		try {
			expired = storage.list(PREFIX).stream()
					.filter(object -> object.lastModified() != null && object.lastModified().isBefore(cutoff))
					.toList();
		}
		catch (RuntimeException ex) {
			log.warn("Could not list credentials files to expire them: {}", ex.getMessage());
			return;
		}
		if (expired.isEmpty()) {
			return;
		}

		int deleted = 0;
		for (ObjectStorage.StoredObjectSummary object : expired) {
			try {
				storage.delete(object.key());
				deleted++;
			}
			catch (RuntimeException ex) {
				log.warn("Could not delete expired credentials file {}: {}", object.key(), ex.getMessage());
			}
		}
		log.info("Deleted {} of {} credentials file(s) older than {} hours", deleted, expired.size(),
				RETENTION_HOURS);
	}

	/**
	 * One line of the credentials sheet.
	 *
	 * @param group the class-and-section for a student, or {@code TEACHER}/{@code STAFF} for an
	 *              employee; the "Class/Type" column, which is there so a hundred rows can be sorted
	 *              into the piles they will be handed out in
	 */
	public record Credential(String name, String group, String uniqueId, String temporaryPassword) {
	}

	/** A credentials file on its way back to the caller. */
	public record Download(byte[] bytes, String fileName) {
	}

	// --- internals --------------------------------------------------------------------------------

	private static byte[] render(List<Credential> credentials) {
		try (Workbook workbook = new XSSFWorkbook()) {
			Sheet sheet = workbook.createSheet("Credentials");
			CellStyle headerStyle = headerStyle(workbook);

			Row header = sheet.createRow(0);
			List<String> headers = List.of("Name", "Class/Type", "Unique ID", "Temporary Password");
			for (int i = 0; i < headers.size(); i++) {
				Cell cell = header.createCell(i);
				cell.setCellValue(headers.get(i));
				cell.setCellStyle(headerStyle);
				sheet.setColumnWidth(i, 256 * 28);
			}
			sheet.createFreezePane(0, 1);

			for (int i = 0; i < credentials.size(); i++) {
				Credential credential = credentials.get(i);
				Row row = sheet.createRow(i + 1);
				row.createCell(0).setCellValue(credential.name());
				row.createCell(1).setCellValue(credential.group() == null ? "" : credential.group());
				row.createCell(2).setCellValue(credential.uniqueId());
				row.createCell(3).setCellValue(credential.temporaryPassword());
			}

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			workbook.write(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			// Never let the exception carry the rows: this is the one object holding plain passwords.
			throw new AppException(ErrorType.DEPENDENCY_FAILED, "The credentials file could not be built", ex);
		}
	}

	private static CellStyle headerStyle(Workbook workbook) {
		Font bold = workbook.createFont();
		bold.setBold(true);
		CellStyle style = workbook.createCellStyle();
		style.setFont(bold);
		style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		return style;
	}
}

package com.school.people.api;

import com.school.common.security.HasPermission;
import com.school.people.app.ImportCredentialsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Downloading what an import left behind.
 *
 * <p>Its own controller rather than a path on {@code /students} or {@code /employees}, because one
 * import of either kind produces one of these and the file is not a student or an employee — it is
 * a handful of temporary passwords with a day to live.
 *
 * <p>The bytes are streamed through here rather than handed out as a pre-signed URL, so every read
 * passes the permission check, and the response is marked uncacheable so no proxy or browser keeps
 * a copy of it after the file itself has been deleted.
 */
@RestController
@RequestMapping("/imports")
@Tag(name = "Imports", description = "The credentials files produced by student and employee imports")
public class ImportCredentialsController {

	private final ImportCredentialsService credentials;

	public ImportCredentialsController(ImportCredentialsService credentials) {
		this.credentials = credentials;
	}

	@GetMapping("/credentials/{id}")
	@PreAuthorize(HasPermission.IMPORT_CREDENTIALS_READ)
	@Operation(summary = "Download the credentials of an import",
			description = "The .xlsx of Name, Class/Type, Unique ID and Temporary Password that the import "
					+ "created. This is the only copy of those passwords; it is deleted automatically "
					+ "24 hours after the import, after which this returns 404.")
	public ResponseEntity<byte[]> download(@PathVariable String id) {
		ImportCredentialsService.Download file = credentials.download(id);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(ImportCredentialsService.XLSX_CONTENT_TYPE))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
				.cacheControl(CacheControl.noStore())
				.body(file.bytes());
	}
}

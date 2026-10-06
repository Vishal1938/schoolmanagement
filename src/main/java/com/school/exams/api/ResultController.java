package com.school.exams.api;

import com.school.common.security.HasPermission;
import com.school.exams.app.ReportCard;
import com.school.exams.app.ReportCardService;
import com.school.exams.app.ResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Results.
 *
 * <p>{@code GET /students/{uniqueId}/results} is mapped here rather than in people, because the
 * calculation is this module's and the dependency only runs one way — exams knows about students,
 * people knows nothing about exams.
 */
@RestController
@Tag(name = "Exams", description = "Exams, marks entry and results")
public class ResultController {

	private final ResultService results;
	private final ReportCardService reportCards;

	public ResultController(ResultService results, ReportCardService reportCards) {
		this.results = results;
		this.reportCards = reportCards;
	}

	@GetMapping("/students/{uniqueId}/results")
	@PreAuthorize(HasPermission.AUTHENTICATED)
	@Operation(summary = "One student's results for a session",
			description = "Defaults to the active session. ADMIN and teachers may read any student's and see "
					+ "MARKS_ENTRY exams too; a student may read only their own, and only PUBLISHED exams.")
	public StudentResultsResponse forStudent(
			@PathVariable String uniqueId,
			@RequestParam(required = false) String sessionId) {
		return results.forStudent(uniqueId, sessionId);
	}

	/**
	 * Served inline so a browser or the frontend's print dialog shows the card rather than dropping a
	 * file in the downloads folder; the filename is still there for whoever does save it.
	 */
	@GetMapping(value = "/students/{uniqueId}/report-card/{examId}.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	@PreAuthorize(HasPermission.AUTHENTICATED)
	@Operation(summary = "One student's report card for one exam, as a PDF",
			description = "ADMIN and teachers may print any student's; a student may print only their own. "
					+ "PUBLISHED exams only, for everybody: an exam still in marks entry has no result to state.")
	public ResponseEntity<byte[]> reportCard(@PathVariable String uniqueId, @PathVariable String examId) {
		ReportCard card = reportCards.forStudent(uniqueId, examId);
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.inline().filename(card.fileName()).build().toString())
				.cacheControl(CacheControl.noStore())
				.body(card.pdf());
	}

	@GetMapping("/exams/{examId}/results")
	@PreAuthorize(HasPermission.STUDENT_READ_BASIC_OR_FULL)
	@Operation(summary = "A class-section's result sheet for one exam",
			description = "One row per ACTIVE student, best rank first. Ties share a rank and the next rank "
					+ "skips: 1, 2, 2, 4.")
	public ClassResultSheetResponse forClass(
			@PathVariable String examId,
			@RequestParam String classId,
			@RequestParam String section) {
		return results.forClass(examId, classId, section);
	}
}

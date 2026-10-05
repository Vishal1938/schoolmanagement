package com.school.exams.api;

import com.school.common.security.HasPermission;
import com.school.exams.app.MarksService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Marks entry: one subject, one class-section, one grid at a time.
 *
 * <p>{@code MARKS_WRITE} gate-keeps the endpoint; <em>when</em> marks may be written — only while
 * the exam is in MARKS_ENTRY — is enforced in the service, because it depends on the exam.
 */
@RestController
@RequestMapping("/exams/{examId}/marks")
@Tag(name = "Exams", description = "Exams, marks entry and results")
public class MarksController {

	private final MarksService marks;

	public MarksController(MarksService marks) {
		this.marks = marks;
	}

	@GetMapping
	@PreAuthorize(HasPermission.MARKS_WRITE)
	@Operation(summary = "The marks grid for one subject and class-section",
			description = "Every ACTIVE student in roll order with their mark, or null where none has been "
					+ "entered. Check `editable` before offering a form.")
	public MarksEntryResponse grid(
			@PathVariable String examId,
			@RequestParam String classId,
			@RequestParam String section,
			@RequestParam String subjectId) {
		return marks.grid(examId, classId, section, subjectId);
	}

	@PutMapping
	@PreAuthorize(HasPermission.MARKS_WRITE)
	@Operation(summary = "Save the marks grid",
			description = "An upsert per row: a student left out keeps the mark they had, so entering half "
					+ "a class does not wipe the other half. Any teacher may enter marks for any class. 422 "
					+ "unless the exam is in MARKS_ENTRY; 400 if a mark is above the paper's maxMarks or an "
					+ "entry names somebody who is not on this roll.")
	public MarksEntryResponse save(
			@PathVariable String examId,
			@RequestParam String classId,
			@RequestParam String section,
			@RequestParam String subjectId,
			@Valid @RequestBody MarksEntryRequest request) {
		return marks.save(examId, classId, section, subjectId, request);
	}
}

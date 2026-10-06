package com.school.exams.api;

import java.util.List;
import java.util.Map;

import com.school.common.security.HasPermission;
import com.school.exams.app.ExamService;
import com.school.exams.domain.Exam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Exams and their status. */
@RestController
@RequestMapping("/exams")
@Tag(name = "Exams", description = "Exams, marks entry and results")
public class ExamController {

	private final ExamService exams;

	public ExamController(ExamService exams) {
		this.exams = exams;
	}

	@GetMapping
	@PreAuthorize(HasPermission.AUTHENTICATED)
	@Operation(summary = "Exams in a session",
			description = "Defaults to the active session. ADMIN and teachers see every status; a student "
					+ "sees only PUBLISHED exams, and only those their own class sits.")
	public List<ExamResponse> list(
			@RequestParam(required = false) String sessionId,
			@RequestParam(required = false) String classId) {
		List<Exam> found = exams.list(sessionId, classId);
		Map<String, String> subjectNames = exams.subjectNames(found);
		return found.stream().map(exam -> ExamResponse.of(exam, subjectNames)).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.EXAM_MANAGE)
	@Operation(summary = "Create an exam",
			description = "Starts as a DRAFT in the active session. Every classId and every schedule "
					+ "subjectId must exist, and passMarks must not exceed maxMarks.")
	public ExamResponse create(@Valid @RequestBody ExamRequest request) {
		Exam exam = exams.create(request);
		return ExamResponse.of(exam, exams.subjectNames(List.of(exam)));
	}

	@PutMapping("/{id}")
	@PreAuthorize(HasPermission.EXAM_MANAGE)
	@Operation(summary = "Replace an exam's name, classes and schedule",
			description = "Only while it is a DRAFT: once marks exist, changing what a paper is out of "
					+ "would silently invalidate them. 422 otherwise.")
	public ExamResponse update(@PathVariable String id, @Valid @RequestBody ExamRequest request) {
		Exam exam = exams.update(id, request);
		return ExamResponse.of(exam, exams.subjectNames(List.of(exam)));
	}

	@PutMapping("/{id}/status")
	@PreAuthorize(HasPermission.EXAM_MANAGE)
	@Operation(summary = "Move an exam between DRAFT, MARKS_ENTRY and PUBLISHED",
			description = "DRAFT cannot go straight to PUBLISHED. Publishing requires a mark for every "
					+ "student in every subject; when some are missing it answers 422 with a `missing` array "
					+ "naming each student and subject.")
	public ExamResponse changeStatus(@PathVariable String id, @Valid @RequestBody ExamStatusRequest request) {
		Exam exam = exams.changeStatus(id, request);
		return ExamResponse.of(exam, exams.subjectNames(List.of(exam)));
	}
}

package com.school.academics.api;

import java.util.List;

import com.school.academics.app.SubjectService;
import com.school.common.security.HasPermission;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Subjects. Shared by every class, so they are created once and referenced by id. */
@RestController
@RequestMapping("/subjects")
@Tag(name = "Academics", description = "Sessions, classes, subjects and teaching assignments")
public class SubjectController {

	private final SubjectService subjects;

	public SubjectController(SubjectService subjects) {
		this.subjects = subjects;
	}

	@GetMapping
	@PreAuthorize(HasPermission.AUTHENTICATED)
	@Operation(summary = "List the subjects, by name")
	public List<SubjectResponse> list() {
		return subjects.list().stream().map(SubjectResponse::from).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.ACADEMICS_MANAGE)
	@Operation(summary = "Add a subject", description = "The code is stored upper-case and must be unique.")
	public SubjectResponse create(@Valid @RequestBody SubjectRequest request) {
		return SubjectResponse.from(subjects.create(request));
	}

	@PutMapping("/{id}")
	@PreAuthorize(HasPermission.ACADEMICS_MANAGE)
	@Operation(summary = "Rename a subject or change its code",
			description = "Classes refer to subjects by id, so neither change affects which classes teach it.")
	public SubjectResponse update(@PathVariable String id, @Valid @RequestBody SubjectRequest request) {
		return SubjectResponse.from(subjects.update(id, request));
	}
}

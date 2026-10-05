package com.school.academics.api;

import java.util.List;

import com.school.academics.app.SchoolClassService;
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

/** Classes, their sections and subjects, and the teachers assigned to them. */
@RestController
@RequestMapping("/classes")
@Tag(name = "Academics", description = "Sessions, classes, subjects and teaching assignments")
public class ClassController {

	private final SchoolClassService classes;

	public ClassController(SchoolClassService classes) {
		this.classes = classes;
	}

	@GetMapping
	@PreAuthorize(HasPermission.AUTHENTICATED)
	@Operation(summary = "List the classes in display order, with their sections, subjects and assignments")
	public List<ClassResponse> list() {
		return classes.list().stream().map(ClassResponse::from).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.ACADEMICS_MANAGE)
	@Operation(summary = "Add a class",
			description = "Section names are stored upper-case. Every subject id must already exist.")
	public ClassResponse create(@Valid @RequestBody ClassRequest request) {
		return ClassResponse.from(classes.create(request));
	}

	@PutMapping("/{id}")
	@PreAuthorize(HasPermission.ACADEMICS_MANAGE)
	@Operation(summary = "Replace a class's name, order, sections and subjects",
			description = "Existing assignments are kept, except those pointing at a section or subject this "
					+ "update removes.")
	public ClassResponse update(@PathVariable String id, @Valid @RequestBody ClassRequest request) {
		return ClassResponse.from(classes.update(id, request));
	}

	@PutMapping("/{id}/assignments")
	@PreAuthorize(HasPermission.ACADEMICS_MANAGE)
	@Operation(summary = "Set the class teacher per section and the teacher per subject and section",
			description = "A full replace: a section left out of the body ends up with nobody assigned. "
					+ "Teachers are given by uniqueId — see GET /users/teachers. An unknown section, a subject "
					+ "the class does not teach, or an id that is not an active teacher returns 400 with every "
					+ "problem listed and nothing written.")
	public ClassResponse updateAssignments(@PathVariable String id,
			@Valid @RequestBody ClassAssignmentsRequest request) {
		return ClassResponse.from(classes.updateAssignments(id, request));
	}
}

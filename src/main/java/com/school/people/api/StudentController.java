package com.school.people.api;

import com.school.common.fees.FeeStatus;
import com.school.common.pagination.PageResponse;
import com.school.common.security.HasPermission;
import com.school.people.app.StudentSearch;
import com.school.people.app.StudentService;
import com.school.people.domain.Student;
import com.school.people.domain.StudentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Students.
 *
 * <p>{@code @PreAuthorize} here only answers "may this caller use this endpoint at all". Which
 * fields come back, and whether a student may read the record they asked for, are decided in
 * {@link StudentService} from the security context — see {@link StudentView}.
 */
@RestController
@RequestMapping("/students")
@Tag(name = "Students", description = "Admission, lookup, editing, status and password resets")
public class StudentController {

	private final StudentService students;

	public StudentController(StudentService students) {
		this.students = students;
	}

	@GetMapping
	@PreAuthorize(HasPermission.STUDENT_READ_BASIC_OR_FULL)
	@Operation(summary = "List students",
			description = "Filters are ANDed and all optional. q matches the name, the uniqueId or the family "
					+ "phone number as a prefix. feeStatus filters on the derived status — PAID, PARTIAL, DUE or "
					+ "OVERDUE — not on a stored field. Rows are teacher-safe and carry no amounts; sorted by "
					+ "name unless a sort is given.")
	public PageResponse<StudentListItem> list(
			@RequestParam(required = false) String classId,
			@RequestParam(required = false) String section,
			@RequestParam(required = false) StudentStatus status,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) FeeStatus feeStatus,
			Pageable pageable) {
		Page<Student> page = students.search(new StudentSearch(classId, section, status, q, feeStatus), pageable);
		return PageResponse.of(students.toListItems(page.getContent()), page.getPageable(), page.getTotalElements());
	}

	@GetMapping("/me")
	@PreAuthorize(HasPermission.AUTHENTICATED)
	@Operation(summary = "Your own student record",
			description = "For the student (or the parent signed in as them). 404 for a login that is not a "
					+ "student's.")
	public StudentSelfView me() {
		return students.viewSelf();
	}

	@GetMapping("/{uniqueId}")
	@PreAuthorize(HasPermission.AUTHENTICATED)
	@Operation(summary = "One student, projected for the caller",
			description = "ADMIN gets every field, a teacher gets the teacher view with a fee status but no "
					+ "amounts, and a student gets their own record only — asking for somebody else's is 403.")
	public StudentView get(@PathVariable String uniqueId) {
		return students.view(uniqueId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.STUDENT_WRITE)
	@Operation(summary = "Admit a student",
			description = "Issues the STU id, writes the student and creates the login in one transaction. The "
					+ "temporary password comes back in this response and nowhere else, ever.")
	public StudentCreatedResponse admit(@Valid @RequestBody StudentRequest request) {
		return students.admit(request);
	}

	@PutMapping("/{uniqueId}")
	@PreAuthorize(HasPermission.STUDENT_WRITE)
	@Operation(summary = "Update a student's details",
			description = "The uniqueId never changes, and the status has its own endpoint.")
	public StudentAdminView update(@PathVariable String uniqueId, @Valid @RequestBody StudentRequest request) {
		return students.update(uniqueId, request);
	}

	@PatchMapping("/{uniqueId}/status")
	@PreAuthorize(HasPermission.STUDENT_WRITE)
	@Operation(summary = "Set a student's status to ACTIVE, LEFT or ALUMNI",
			description = "Students are never deleted: marks, invoices and receipts point at them.")
	public StudentAdminView changeStatus(@PathVariable String uniqueId,
			@Valid @RequestBody StudentStatusRequest request) {
		return students.changeStatus(uniqueId, request);
	}

	@PostMapping("/{uniqueId}/reset-password")
	@PreAuthorize(HasPermission.STUDENT_WRITE)
	@Operation(summary = "Issue a new temporary password",
			description = "Forces a change on next login and ends every session the account had. Returned "
					+ "once; calling again issues another rather than repeating it.")
	public TemporaryPasswordResponse resetPassword(@PathVariable String uniqueId) {
		return new TemporaryPasswordResponse(uniqueId, students.resetPassword(uniqueId));
	}
}

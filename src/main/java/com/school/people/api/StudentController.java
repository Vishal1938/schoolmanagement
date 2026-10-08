package com.school.people.api;

import com.school.common.fees.FeeStatus;
import com.school.common.pagination.PageResponse;
import com.school.common.security.HasPermission;
import com.school.people.app.ImportCredentialsService;
import com.school.people.app.ImportTemplates;
import com.school.people.app.StudentImportService;
import com.school.people.app.StudentPromotionService;
import com.school.people.app.StudentSearch;
import com.school.people.app.StudentService;
import com.school.people.domain.Student;
import com.school.people.domain.StudentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

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
	private final StudentImportService imports;
	private final ImportTemplates templates;
	private final StudentPromotionService promotions;

	public StudentController(StudentService students, StudentImportService imports, ImportTemplates templates,
			StudentPromotionService promotions) {
		this.students = students;
		this.imports = imports;
		this.templates = templates;
		this.promotions = promotions;
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

	// --- bulk import ------------------------------------------------------------------------------

	@GetMapping("/import/template")
	@PreAuthorize(HasPermission.STUDENT_WRITE)
	@Operation(summary = "Download the student import template",
			description = "An .xlsx with the header row, one example row, an Instructions sheet, and "
					+ "dropdowns for Gender, Class and Section filled in from this school's own academics. "
					+ "Generated per request, so it is always current.")
	public ResponseEntity<byte[]> importTemplate() {
		return xlsx(templates.students(), "student-import-template.xlsx");
	}

	@PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize(HasPermission.STUDENT_WRITE)
	@Operation(summary = "Import students from a filled-in template",
			description = "Up to 5 MB of .xlsx. Every row is validated first: if any of them has a problem, "
					+ "nothing at all is created and the response is {valid: false, errors: [{row, column, "
					+ "message}]} with every bad cell listed. Otherwise each student is admitted exactly as "
					+ "POST /students would, and the response carries the count and a credentialsFileId for "
					+ "GET /imports/credentials/{id}. Both outcomes are 200.")
	public ImportResult importStudents(@RequestParam("file") MultipartFile file) {
		return imports.importStudents(file);
	}

	// --- promotion --------------------------------------------------------------------------------

	@PostMapping("/promote/preview")
	@PreAuthorize(HasPermission.STUDENT_WRITE)
	@Operation(summary = "Preview a promotion",
			description = "Who would move and where to, per mapping, with the students listed in section "
					+ "and roll order. Writes nothing. A mapping with toClassId null is the final class, "
					+ "whose students would graduate. excludeUniqueIds is ignored here — this is the list "
					+ "the admin reads in order to decide who goes in it.")
	public PromotionPreview previewPromotion(@Valid @RequestBody PromotionRequest request) {
		return promotions.preview(request);
	}

	@PostMapping("/promote")
	@PreAuthorize(HasPermission.STUDENT_WRITE)
	@Operation(summary = "Promote students into the next session",
			description = "Moves the ACTIVE students of each mapped class out of the active session and into "
					+ "toSessionId, keeping their section and roll number and pushing the old enrollment "
					+ "into the student's enrollment history. toClassId null marks that class ALUMNI; "
					+ "students named in excludeUniqueIds stay in the same class in the new session. One "
					+ "transaction per mapping. This does NOT change which session is active.")
	public PromotionResult promote(@Valid @RequestBody PromotionRequest request) {
		return promotions.promote(request);
	}

	private static ResponseEntity<byte[]> xlsx(byte[] bytes, String fileName) {
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(ImportCredentialsService.XLSX_CONTENT_TYPE))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
				.body(bytes);
	}
}

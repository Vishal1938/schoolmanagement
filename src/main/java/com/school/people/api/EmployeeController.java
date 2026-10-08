package com.school.people.api;

import com.school.common.pagination.PageResponse;
import com.school.common.security.HasPermission;
import com.school.people.app.EmployeeImportService;
import com.school.people.app.EmployeeSearch;
import com.school.people.app.EmployeeService;
import com.school.people.app.ImportCredentialsService;
import com.school.people.app.ImportTemplates;
import com.school.people.domain.Employee;
import com.school.people.domain.EmployeeStatus;
import com.school.people.domain.EmployeeType;
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
 * Employees — teachers and non-teaching staff.
 *
 * <p>As with students, {@code @PreAuthorize} only answers "may this caller use this endpoint". Which
 * fields come back, and whether somebody may read the record they asked for, are decided in
 * {@link EmployeeService} from the security context.
 */
@RestController
@RequestMapping("/employees")
@Tag(name = "Employees", description = "Teachers and staff: hiring, lookup, status and password resets")
public class EmployeeController {

	private final EmployeeService employees;
	private final EmployeeImportService imports;
	private final ImportTemplates templates;

	public EmployeeController(EmployeeService employees, EmployeeImportService imports, ImportTemplates templates) {
		this.employees = employees;
		this.imports = imports;
		this.templates = templates;
	}

	@GetMapping
	@PreAuthorize(HasPermission.EMPLOYEE_READ)
	@Operation(summary = "List employees",
			description = "Filters are ANDed and all optional. q matches the name, the uniqueId or the phone "
					+ "number as a prefix. The bank account appears only as its last four digits; open the "
					+ "employee for the full number.")
	public PageResponse<EmployeeListItem> list(
			@RequestParam(required = false) EmployeeType type,
			@RequestParam(required = false) EmployeeStatus status,
			@RequestParam(required = false) String q,
			Pageable pageable) {
		Page<Employee> page = employees.search(new EmployeeSearch(type, status, q), pageable);
		return PageResponse.of(employees.toListItems(page.getContent()), page.getPageable(), page.getTotalElements());
	}

	@GetMapping("/me")
	@PreAuthorize(HasPermission.AUTHENTICATED)
	@Operation(summary = "Your own employee record",
			description = "For a teacher or staff member. 404 for a login that is not an employee's.")
	public EmployeeSelfView me() {
		return employees.viewSelf();
	}

	@GetMapping("/{uniqueId}")
	@PreAuthorize(HasPermission.AUTHENTICATED)
	@Operation(summary = "One employee",
			description = "ADMIN gets every field with the bank account number decrypted, and so does the "
					+ "employee for their own record. Anybody else gets 403.")
	public EmployeeView get(@PathVariable String uniqueId) {
		return employees.view(uniqueId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.EMPLOYEE_WRITE)
	@Operation(summary = "Take on an employee",
			description = "Issues the EMP id, writes the record and creates the login in one transaction. A "
					+ "TEACHER always gets a login; a STAFF member only with hasLogin=true, and "
					+ "temporaryPassword is null when none was created.")
	public EmployeeSavedResponse hire(@Valid @RequestBody EmployeeRequest request) {
		return employees.hire(request);
	}

	@PutMapping("/{uniqueId}")
	@PreAuthorize(HasPermission.EMPLOYEE_WRITE)
	@Operation(summary = "Update an employee's details",
			description = "Switching hasLogin on for a staff member creates their login now and returns its "
					+ "temporary password. Omitting the bank block keeps the details already on file.")
	public EmployeeSavedResponse update(@PathVariable String uniqueId,
			@Valid @RequestBody EmployeeRequest request) {
		return employees.update(uniqueId, request);
	}

	@PatchMapping("/{uniqueId}/status")
	@PreAuthorize(HasPermission.EMPLOYEE_WRITE)
	@Operation(summary = "Set an employee's status to ACTIVE or LEFT",
			description = "Their login is disabled or re-enabled to match, and LEFT ends their sessions.")
	public EmployeeAdminView changeStatus(@PathVariable String uniqueId,
			@Valid @RequestBody EmployeeStatusRequest request) {
		return employees.changeStatus(uniqueId, request);
	}

	@PostMapping("/{uniqueId}/reset-password")
	@PreAuthorize(HasPermission.EMPLOYEE_WRITE)
	@Operation(summary = "Issue a new temporary password",
			description = "Forces a change on next login and ends every session the account had. Returned "
					+ "once. 400 if this employee has no login.")
	public TemporaryPasswordResponse resetPassword(@PathVariable String uniqueId) {
		return new TemporaryPasswordResponse(uniqueId, employees.resetPassword(uniqueId));
	}

	// --- bulk import ------------------------------------------------------------------------------

	@GetMapping("/import/template")
	@PreAuthorize(HasPermission.EMPLOYEE_WRITE)
	@Operation(summary = "Download the employee import template",
			description = "An .xlsx with the header row, one example row, an Instructions sheet, and "
					+ "dropdowns for Type, Gender and Can Login. The school's subject names are listed on "
					+ "the Instructions sheet, because the Subjects column takes names rather than ids.")
	public ResponseEntity<byte[]> importTemplate() {
		return xlsx(templates.employees(), "employee-import-template.xlsx");
	}

	@PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize(HasPermission.EMPLOYEE_WRITE)
	@Operation(summary = "Import employees from a filled-in template",
			description = "Up to 5 MB of .xlsx. Every row is validated first: if any of them has a problem, "
					+ "nothing at all is created and the response is {valid: false, errors: [{row, column, "
					+ "message}]}. Otherwise each employee is hired exactly as POST /employees would. "
					+ "credentialsFileId is null when no row produced a login. Both outcomes are 200.")
	public ImportResult importEmployees(@RequestParam("file") MultipartFile file) {
		return imports.importEmployees(file);
	}

	private static ResponseEntity<byte[]> xlsx(byte[] bytes, String fileName) {
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(ImportCredentialsService.XLSX_CONTENT_TYPE))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
				.body(bytes);
	}
}

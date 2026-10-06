package com.school.people.api;

import java.util.List;

import com.school.common.security.HasPermission;
import com.school.people.app.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The teacher picker at {@code GET /users/teachers}.
 *
 * <p>The path belongs to B4 and to the frontend's assignment screen, but the data moved here in B6:
 * the register of who teaches is the employee directory, not the login table. The response shape is
 * unchanged, so nothing on the frontend had to move with it.
 *
 * <p>It lives in people rather than in auth because the dependency only runs one way — people knows
 * about logins, auth knows nothing about employees — and a controller in auth reading employees
 * would make the two modules circular.
 */
@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "Lookups across the people who can be referred to by uniqueId")
public class TeacherDirectoryController {

	private final EmployeeService employees;

	public TeacherDirectoryController(EmployeeService employees) {
		this.employees = employees;
	}

	@GetMapping("/teachers")
	@PreAuthorize(HasPermission.AUTHENTICATED)
	@Operation(summary = "Every active teacher, as a picker list",
			description = "uniqueId and name only, from the employee directory: employeeType TEACHER with "
					+ "status ACTIVE. Carries no contact details, so it is safe for any logged-in user.")
	public List<TeacherRefResponse> teachers() {
		return employees.activeTeachers().stream().map(TeacherRefResponse::of).toList();
	}
}

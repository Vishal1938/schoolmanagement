package com.school.attendance.api;

import java.time.LocalDate;

import com.school.attendance.app.EmployeeAttendanceService;
import com.school.attendance.app.StudentAttendanceService;
import com.school.common.security.HasPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Marking and reading attendance.
 *
 * <p>{@code @PreAuthorize} answers "may this caller mark registers at all". <em>When</em> a register
 * may be written — not in the future, not on a holiday or a closed day, and for a teacher only
 * inside the edit window — is decided in the services, because it depends on the date and on what
 * is already stored. Whether somebody may read one person's record is decided there too, because it
 * depends on whose record was asked for.
 */
@RestController
@RequestMapping("/attendance")
@Tag(name = "Attendance", description = "Holidays, class registers and attendance records")
public class AttendanceController {

	private final StudentAttendanceService studentAttendance;
	private final EmployeeAttendanceService employeeAttendance;

	public AttendanceController(StudentAttendanceService studentAttendance,
			EmployeeAttendanceService employeeAttendance) {
		this.studentAttendance = studentAttendance;
		this.employeeAttendance = employeeAttendance;
	}

	// --- students ---------------------------------------------------------------------------------

	@GetMapping("/class/{classId}/{section}")
	@PreAuthorize(HasPermission.ATTENDANCE_MARK_STUDENT)
	@Operation(summary = "A class-section's register for one day",
			description = "Every ACTIVE student in roll order, each with their status for the date or null if "
					+ "nobody has marked them. Check `editable` before offering a form: a teacher past the "
					+ "edit window gets false and a `lockedReason`.")
	public ClassRegisterResponse classRegister(
			@PathVariable String classId,
			@PathVariable String section,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return studentAttendance.register(classId, section, date);
	}

	@PutMapping("/class/{classId}/{section}")
	@PreAuthorize(HasPermission.ATTENDANCE_MARK_STUDENT)
	@Operation(summary = "Submit or correct a class-section's register",
			description = "A full replace: a student left out ends up unmarked. Any teacher may mark any "
					+ "class. 422 for a future date, a holiday, a non-working day, or a teacher past the edit "
					+ "window; 400 if an entry names somebody who is not on this roll.")
	public ClassRegisterResponse markClass(
			@PathVariable String classId,
			@PathVariable String section,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@Valid @RequestBody MarkStudentAttendanceRequest request) {
		return studentAttendance.mark(classId, section, date, request);
	}

	@GetMapping("/students/{uniqueId}")
	@PreAuthorize(HasPermission.AUTHENTICATED)
	@Operation(summary = "One student's attendance over a range",
			description = "Marked days plus the totals. ADMIN and teachers may read any student's; a student "
					+ "may read only their own.")
	public AttendanceHistoryResponse studentHistory(
			@PathVariable String uniqueId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return studentAttendance.studentHistory(uniqueId, from, to);
	}

	// --- employees --------------------------------------------------------------------------------

	@GetMapping("/employees")
	@PreAuthorize(HasPermission.ATTENDANCE_MARK_EMPLOYEE)
	@Operation(summary = "The staff register for one day",
			description = "Every ACTIVE employee by name, each with their status for the date or null.")
	public EmployeeRegisterResponse employeeRegister(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return employeeAttendance.register(date);
	}

	@PutMapping("/employees")
	@PreAuthorize(HasPermission.ATTENDANCE_MARK_EMPLOYEE)
	@Operation(summary = "Submit or correct the staff register",
			description = "A full replace, with the same date rules as the class register.")
	public EmployeeRegisterResponse markEmployees(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@Valid @RequestBody MarkEmployeeAttendanceRequest request) {
		return employeeAttendance.mark(date, request);
	}

	@GetMapping("/employees/{uniqueId}")
	@PreAuthorize(HasPermission.AUTHENTICATED)
	@Operation(summary = "One employee's attendance over a range",
			description = "Marked days plus the totals. ADMIN may read anyone's; an employee only their own.")
	public AttendanceHistoryResponse employeeHistory(
			@PathVariable String uniqueId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return employeeAttendance.employeeHistory(uniqueId, from, to);
	}
}

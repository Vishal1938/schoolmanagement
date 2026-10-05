package com.school.attendance.api;

import java.time.LocalDate;
import java.util.List;

import com.school.attendance.app.HolidayService;
import com.school.common.security.HasPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The school's holiday calendar. */
@RestController
@RequestMapping("/holidays")
@Tag(name = "Attendance", description = "Holidays, class registers and attendance records")
public class HolidayController {

	private final HolidayService holidays;

	public HolidayController(HolidayService holidays) {
		this.holidays = holidays;
	}

	@GetMapping
	@PreAuthorize(HasPermission.AUTHENTICATED)
	@Operation(summary = "Holidays in a date range",
			description = "Both ends inclusive, oldest first. Weekly closures are not in here: which days "
					+ "of the week the school works is academicSettings.workingDays.")
	public List<HolidayResponse> list(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return holidays.between(from, to).stream().map(HolidayResponse::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.HOLIDAY_MANAGE)
	@Operation(summary = "Declare a holiday",
			description = "One per date; a second on the same date is 409. Attendance cannot be marked on it.")
	public HolidayResponse create(@Valid @RequestBody HolidayRequest request) {
		return HolidayResponse.of(holidays.create(request));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(HasPermission.HOLIDAY_MANAGE)
	@Operation(summary = "Remove a holiday",
			description = "Attendance already marked on other days is untouched; the day becomes markable.")
	public void delete(@PathVariable String id) {
		holidays.delete(id);
	}
}

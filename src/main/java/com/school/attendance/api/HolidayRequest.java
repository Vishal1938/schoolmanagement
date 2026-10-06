package com.school.attendance.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /holidays}. */
public record HolidayRequest(
		@NotNull LocalDate date,
		@NotBlank @Size(max = 120) String name) {
}

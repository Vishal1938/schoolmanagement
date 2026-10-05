package com.school.attendance.api;

import java.time.LocalDate;

import com.school.attendance.domain.Holiday;

/** A holiday as the API returns it. */
public record HolidayResponse(String id, LocalDate date, String name) {

	public static HolidayResponse of(Holiday holiday) {
		return new HolidayResponse(holiday.getId(), holiday.getDate(), holiday.getName());
	}
}

package com.school.attendance.domain;

import java.time.Instant;
import java.time.LocalDate;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A day the school is shut. Attendance cannot be marked on one, and B16's calendars read the same
 * collection.
 *
 * <p>The weekly closure is not in here: which days of the week the school works is a school-wide
 * setting ({@code academicSettings.workingDays}), and repeating every Sunday as a holiday document
 * would be a second, drifting copy of it.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(Holiday.COLLECTION)
public class Holiday {

	public static final String COLLECTION = "holidays";

	@Id
	private String id;

	/** Unique: a day is either a holiday or it is not, and two names for one day is a data entry slip. */
	@Indexed(name = "holidays_date_idx", unique = true)
	private LocalDate date;

	/** What it is called, e.g. "Diwali". Printed on calendars. */
	private String name;

	private Instant createdAt;
}

package com.school.academics.domain;

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
 * One academic year, e.g. {@code 2026-27}. Everything dated — enrollments, attendance, exams,
 * invoices — hangs off a session, so the rest of the system asks {@code AcademicContext} which one is
 * current rather than reading a date.
 *
 * <p>Exactly one session is {@code active} at a time. That is a service rule, not an index: Mongo
 * cannot express "at most one document with active=true", and a unique partial index would make
 * switching sessions a two-step dance that can fail halfway.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(AcademicSession.COLLECTION)
public class AcademicSession {

	public static final String COLLECTION = "academic_sessions";

	@Id
	private String id;

	/** How the school writes the year. Free text, because the convention differs per school. */
	@Indexed(name = "academic_sessions_name_idx", unique = true)
	private String name;

	private LocalDate startDate;

	private LocalDate endDate;

	/** Queried on nearly every request through {@code AcademicContext}, hence the index. */
	@Indexed(name = "academic_sessions_active_idx")
	private boolean active;

	private Instant createdAt;

	private Instant updatedAt;
}

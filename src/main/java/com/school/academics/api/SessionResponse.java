package com.school.academics.api;

import java.time.LocalDate;

import com.school.academics.domain.AcademicSession;

/** An academic session as the API returns it. */
public record SessionResponse(
		String id,
		String name,
		LocalDate startDate,
		LocalDate endDate,
		boolean active) {

	public static SessionResponse from(AcademicSession session) {
		return new SessionResponse(session.getId(), session.getName(), session.getStartDate(),
				session.getEndDate(), session.isActive());
	}
}

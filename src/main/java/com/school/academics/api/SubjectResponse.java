package com.school.academics.api;

import com.school.academics.domain.Subject;

/** A subject as the API returns it. */
public record SubjectResponse(String id, String name, String code) {

	public static SubjectResponse from(Subject subject) {
		return new SubjectResponse(subject.getId(), subject.getName(), subject.getCode());
	}
}

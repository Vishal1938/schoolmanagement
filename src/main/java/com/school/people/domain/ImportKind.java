package com.school.people.domain;

/** Which import an {@link ImportJob} belongs to. Part of its key, so the two cannot collide. */
public enum ImportKind {
	STUDENTS,
	EMPLOYEES
}

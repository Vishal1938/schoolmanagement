package com.school.academics.domain;

import java.time.Instant;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A teachable subject. Subjects exist once for the school and are attached to classes by id, so
 * renaming "Maths" to "Mathematics" does not have to be repeated per class.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(Subject.COLLECTION)
public class Subject {

	public static final String COLLECTION = "subjects";

	@Id
	private String id;

	@Indexed(name = "subjects_name_idx", unique = true)
	private String name;

	/** Short code used on report cards and mark sheets, e.g. {@code ENG}. Stored upper-case. */
	@Indexed(name = "subjects_code_idx", unique = true)
	private String code;

	private Instant createdAt;

	private Instant updatedAt;
}

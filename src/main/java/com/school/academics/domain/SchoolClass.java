package com.school.academics.domain;

import java.time.Instant;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A class such as "Class 1", with its sections, its subjects and who teaches them.
 *
 * <p>Named {@code SchoolClass} rather than {@code Class} so it does not collide with
 * {@link java.lang.Class} in every file that touches it; the collection is still {@code classes}.
 *
 * <p>Sections are plain strings on the class rather than documents of their own. They have no data
 * besides a name, and every query that mentions one ("5-B") starts from the class anyway.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(SchoolClass.COLLECTION)
public class SchoolClass {

	public static final String COLLECTION = "classes";

	@Id
	private String id;

	@Indexed(name = "classes_name_idx", unique = true)
	private String name;

	/**
	 * Display order. Explicit because names do not sort usefully — "Class 10" comes before "Class 2"
	 * alphabetically, and schools mix in "Nursery" and "LKG".
	 */
	@Indexed(name = "classes_order_idx")
	private int order;

	/** Section names, upper-case and unique within the class, in display order. */
	private List<String> sections;

	/** Ids of the {@link Subject}s taught in this class. */
	private List<String> subjectIds;

	/** One entry per section that has anybody assigned; sections with nobody yet are simply absent. */
	private List<SectionAssignment> assignments;

	private Instant createdAt;

	private Instant updatedAt;
}

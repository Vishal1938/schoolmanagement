package com.school.schoolconfig.domain;

import java.time.Instant;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The one configuration document of this deployment: identity, landing-page content, grading scheme
 * and academic settings.
 *
 * <p>There is exactly one of these per school, enforced by the fixed {@link #SINGLETON_ID} rather
 * than by a query: a second insert collides on {@code _id}, so two instances booting against an
 * empty database cannot both seed. {@code code} is not editable through the API — it comes from
 * {@code SCHOOL_CODE}, because B3 builds every unique ID from it.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(SchoolConfig.COLLECTION)
public class SchoolConfig {

	public static final String COLLECTION = "school_config";

	/** Fixed id of the single document; see the class comment. */
	public static final String SINGLETON_ID = "school-config";

	@Id
	private String id;

	/** Guards against two admins overwriting each other through {@code PUT /school/config}. */
	@Version
	private Long version;

	private String code;

	private Identity identity;

	private Landing landing;

	private GradingScheme gradingScheme;

	private AcademicSettings academicSettings;

	private Instant createdAt;

	private Instant updatedAt;
}

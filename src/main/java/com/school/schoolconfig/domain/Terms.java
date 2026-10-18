package com.school.schoolconfig.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What this institution calls the four things every screen in the frontend has a label for.
 *
 * <p>The backend never reads these. They exist so a college deployment can say "Course" where a
 * school says "Class" without a second build of the frontend — the data model, the URLs and the
 * field names stay {@code classId} and {@code section} everywhere, because renaming those would mean
 * renaming half the API for a vocabulary change (CLAUDE.md rule 6 is about configuration, not about
 * forking the contract).
 *
 * <p>Serialised as {@code class} rather than {@code schoolClass}: that is the word the frontend puts
 * on a label, and {@code class} is not a legal Java identifier. The stored Mongo field keeps the
 * Java name.
 *
 * @param institution what the whole place is called — "School", "College"
 * @param schoolClass the unit a student is enrolled in — "Class", "Course", "Programme"
 * @param section     the subdivision of it — "Section", "Year", "Batch"
 * @param teacher     what the staff who teach are called — "Teacher", "Faculty", "Lecturer"
 */
public record Terms(
		String institution,
		@JsonProperty("class") String schoolClass,
		String section,
		String teacher) {

	/** The defaults, and therefore what every deployment made before this existed reads as. */
	public static final Terms SCHOOL = new Terms("School", "Class", "Section", "Teacher");

	/** What a college is expected to set. Not applied automatically — an admin chooses it. */
	public static final Terms COLLEGE = new Terms("College", "Course", "Year", "Faculty");

	/**
	 * This set with any missing word filled in from {@link #SCHOOL}.
	 *
	 * <p>Called on the way out as well as on the way in, so a document stored before this field
	 * existed — or one an admin saved with three of the four words — still answers for all four. A
	 * label the frontend has to render as "undefined" is worse than one that is merely generic.
	 */
	public Terms withDefaults() {
		return new Terms(
				orDefault(institution, SCHOOL.institution()),
				orDefault(schoolClass, SCHOOL.schoolClass()),
				orDefault(section, SCHOOL.section()),
				orDefault(teacher, SCHOOL.teacher()));
	}

	private static String orDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}
}

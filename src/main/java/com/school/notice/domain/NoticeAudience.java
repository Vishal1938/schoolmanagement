package com.school.notice.domain;

import java.util.EnumSet;
import java.util.Set;

import com.school.common.security.Role;

/**
 * Who a notice is addressed to.
 *
 * <p>The vocabulary is deliberately the roles themselves, because that is how a school addresses a
 * notice — "for the teaching staff", "for all students". {@link #CLASS} is the one audience that is
 * narrower than a role: it names a class, and optionally one section of it.
 */
public enum NoticeAudience {

	/** Everybody, whatever their role. */
	ALL,

	STUDENTS,

	TEACHERS,

	STAFF,

	/** One class, or one section of it. Requires {@code classId}; {@code section} may be null. */
	CLASS;

	/**
	 * The audiences a role sees without any further condition.
	 *
	 * <p>This is the one place in the codebase that reads a role rather than a permission, and it is
	 * not an authorization decision in the {@code @PreAuthorize} sense: the audience values
	 * <em>are</em> roles, so expressing "a staff member does not see a students' notice" in
	 * permissions would mean inventing a second, parallel vocabulary that has to be kept in step with
	 * this one. The endpoint is still gated on {@code NOTICE_READ}.
	 *
	 * <p>{@link #CLASS} is in the admin's set and nobody else's. Who else sees a class notice depends
	 * on <em>which</em> class, or on who wrote it, so {@code NoticeService} adds that clause.
	 */
	public static Set<NoticeAudience> plainAudiencesFor(Role role) {
		return switch (role) {
			case ADMIN -> EnumSet.allOf(NoticeAudience.class);
			case TEACHER -> EnumSet.of(ALL, TEACHERS);
			case STUDENT -> EnumSet.of(ALL, STUDENTS);
			case STAFF -> EnumSet.of(ALL, STAFF);
		};
	}
}

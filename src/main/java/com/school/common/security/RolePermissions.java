package com.school.common.security;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.school.common.security.Permission.AI_USE;
import static com.school.common.security.Permission.ATTENDANCE_MARK_STUDENT;
import static com.school.common.security.Permission.EXAM_PAPER_READ;
import static com.school.common.security.Permission.EXAM_PAPER_UPLOAD;
import static com.school.common.security.Permission.FEE_PAY_SELF;
import static com.school.common.security.Permission.FEE_READ_STATUS;
import static com.school.common.security.Permission.MARKS_READ_SELF;
import static com.school.common.security.Permission.MARKS_WRITE;
import static com.school.common.security.Permission.NOTICE_READ;
import static com.school.common.security.Permission.NOTICE_WRITE_CLASS;
import static com.school.common.security.Permission.PAYROLL_READ_SELF;
import static com.school.common.security.Permission.QUIZ_ATTEMPT;
import static com.school.common.security.Permission.QUIZ_MANAGE;
import static com.school.common.security.Permission.STUDENT_READ_BASIC;

/**
 * The fixed set of permissions each {@link Role} carries. This is the only place a role turns into
 * permissions: login puts the result in the token, and everything downstream — the filter chain,
 * {@code @PreAuthorize}, the frontend — sees permissions only.
 *
 * <p>The mapping is deliberately narrow. A teacher gets the basic student projection but no employee
 * access and no fee amounts; a student and a staff member can read their own records and nothing else.
 * Anything finer than "may do this at all" is an object-level check in a service.
 */
public final class RolePermissions {

	private static final Map<Role, Set<Permission>> BY_ROLE = byRole();

	/** The permissions this role carries, as an unmodifiable set. */
	public static Set<Permission> of(Role role) {
		Set<Permission> permissions = BY_ROLE.get(role);
		if (permissions == null) {
			// A role added to the enum without a mapping would otherwise silently get no permissions,
			// which looks like a broken account rather than a missing line here.
			throw new IllegalStateException("No permissions mapped for role " + role);
		}
		return permissions;
	}

	private static Map<Role, Set<Permission>> byRole() {
		Map<Role, Set<Permission>> map = new EnumMap<>(Role.class);
		// The administrator of a single-school deployment is the owner of every feature in it, so this
		// is allOf rather than a list that has to be extended by every later task.
		map.put(Role.ADMIN, EnumSet.allOf(Permission.class));
		map.put(Role.TEACHER, EnumSet.of(
				STUDENT_READ_BASIC,
				ATTENDANCE_MARK_STUDENT,
				MARKS_WRITE,
				EXAM_PAPER_UPLOAD,
				EXAM_PAPER_READ,
				FEE_READ_STATUS,
				NOTICE_WRITE_CLASS,
				NOTICE_READ,
				QUIZ_MANAGE,
				PAYROLL_READ_SELF,
				AI_USE));
		map.put(Role.STUDENT, EnumSet.of(
				MARKS_READ_SELF,
				FEE_READ_STATUS,
				FEE_PAY_SELF,
				NOTICE_READ,
				QUIZ_ATTEMPT));
		map.put(Role.STAFF, EnumSet.of(
				NOTICE_READ,
				PAYROLL_READ_SELF));
		map.replaceAll((role, permissions) -> Collections.unmodifiableSet(permissions));
		return Collections.unmodifiableMap(map);
	}

	private RolePermissions() {
	}
}

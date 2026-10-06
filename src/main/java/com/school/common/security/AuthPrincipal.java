package com.school.common.security;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authenticated caller, as carried in the {@code Authentication} principal. Everything here comes
 * out of the access token, so an authorised request costs no database read.
 *
 * @param userId             the {@code users} document id
 * @param uniqueId           the human ID, e.g. {@code DEMO-STU-26-00142}; what the caller logs in with
 * @param name               display name, for the UI only
 * @param role               shorthand for {@link #permissions}; never authorise on it
 * @param permissions        what this caller may do, resolved from the role at login time
 * @param mustChangePassword true while the account is still on an issued password. B2 part 2 turns this
 *                           into a filter that blocks every endpoint but change-password, me and logout
 * @param profileRef         the student or employee document this login belongs to, null for the
 *                           bootstrap admin, who has no profile
 */
public record AuthPrincipal(
		String userId,
		String uniqueId,
		String name,
		Role role,
		Set<Permission> permissions,
		boolean mustChangePassword,
		String profileRef) {

	/**
	 * Permission names only. No {@code ROLE_} authority is granted, so a {@code hasRole(...)}
	 * expression can never accidentally succeed anywhere in the codebase.
	 */
	public Collection<GrantedAuthority> authorities() {
		return permissions.stream()
				.map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(permission.name()))
				.toList();
	}

	/** The permission names, sorted, for the token claim and for {@code /auth/me}. */
	public List<String> permissionNames() {
		return permissions.stream().map(Permission::name).sorted().toList();
	}
}

package com.school.common.audit;

import com.school.common.security.CurrentUser;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Works out who is acting, from the security context.
 *
 * <p>The distinction between the two sentinels is the presence of a security context at all: a request
 * always has an {@code Authentication} once it has been through the filter chain, even an anonymous
 * one, whereas startup seeding and background jobs have none. So "no authentication object" means
 * {@link AuditActor#SYSTEM} and an anonymous token means {@link AuditActor#ANONYMOUS}.
 */
@Component
public class AuditActorResolver {

	public AuditActor currentActor() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			return AuditActor.SYSTEM;
		}
		if (!authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
			return AuditActor.ANONYMOUS;
		}
		return CurrentUser.principal()
				.map(principal -> new AuditActor(principal.userId(), principal.uniqueId(), principal.role().name()))
				// Authenticated by something other than the JWT filter, which nothing does today.
				.orElseGet(() -> new AuditActor(null, authentication.getName(), null));
	}
}

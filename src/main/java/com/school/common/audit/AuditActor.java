package com.school.common.audit;

/**
 * Who performed an audited action.
 *
 * @param id       the user document id, null when there is no authenticated user
 * @param uniqueId the human ID, or one of the two sentinels below
 * @param role     the actor's role, null until B2 puts a role on the authenticated principal
 */
public record AuditActor(String id, String uniqueId, String role) {

	/** Startup seeding and background jobs: no request, so no user. */
	public static final AuditActor SYSTEM = new AuditActor(null, "SYSTEM", null);

	/** A request that reached us without authentication, e.g. a failed login attempt. */
	public static final AuditActor ANONYMOUS = new AuditActor(null, "ANONYMOUS", null);
}

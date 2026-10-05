package com.school.people.api;

/**
 * What {@code GET /students/{uniqueId}} returns, which depends on who is asking.
 *
 * <p>Three separate types rather than one with nullable fields, because CLAUDE.md rule 2 is about
 * what can physically be serialised: a {@link StudentTeacherView} has no field for an amount, so no
 * change to a service can ever leak one into a teacher's response. The choice is made in
 * {@code StudentService} from the security context, never from a request parameter.
 *
 * <p>Sealed, so adding a fourth projection without deciding who may see it does not compile.
 */
public sealed interface StudentView permits StudentAdminView, StudentTeacherView, StudentSelfView {

	/** Present on every projection: it is the key the frontend routes and caches on. */
	String uniqueId();
}

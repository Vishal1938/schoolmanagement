package com.school.schoolconfig.domain;

/**
 * What kind of institution this deployment serves.
 *
 * <p>It changes <strong>nothing</strong> in the backend's behaviour on its own: every difference that
 * matters is expressed either in {@link Terms} (what the frontend calls a class) or in
 * {@link Modules} (which features are on). This is here so a client can pick a default layout and an
 * icon set without inferring it from the vocabulary, and so an admin screen can offer "switch to
 * college wording" as one button.
 *
 * <p>{@link #SCHOOL} is the default, so a deployment seeded before this existed reads as a school.
 */
public enum InstitutionType {

	SCHOOL,

	COLLEGE
}

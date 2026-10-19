package com.school.people.domain;

/** Where an {@link ImportJob} got to. */
public enum ImportJobStatus {

	/**
	 * The file validated, some rows were written, and then one failed. This is the only status that
	 * makes a re-upload of the same file a resume rather than a fresh import.
	 */
	PARTIAL,

	/** Every row of the file has now been written, whether in one run or across two. */
	COMPLETED
}

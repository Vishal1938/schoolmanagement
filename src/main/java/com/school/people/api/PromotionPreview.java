package com.school.people.api;

import java.util.List;

/**
 * The answer to {@code POST /students/promote/preview}: exactly who would move, and where to.
 *
 * <p>Promotion is the one bulk operation in the system with no undo button — it rewrites the
 * enrollment of every student in the school in an afternoon — so the admin gets to read the list
 * first and pick out who is being held back before committing to it.
 *
 * <p>This is a read. It writes nothing, and running it twice changes nothing.
 */
public record PromotionPreview(
		String fromSessionId,
		String fromSessionName,
		String toSessionId,
		String toSessionName,
		int totalStudents,
		List<Mapping> mappings) {

	/**
	 * One class's worth of the preview.
	 *
	 * @param graduating true when this mapping has no target class: these students finish school and
	 *                   become {@code ALUMNI} rather than moving up
	 * @param students   the ACTIVE students of that class in the active session, in section then roll
	 *                   order — the order a register is read in
	 */
	public record Mapping(
			String fromClassId,
			String fromClassName,
			String toClassId,
			String toClassName,
			boolean graduating,
			int studentCount,
			List<Student> students) {
	}

	/** A student about to be moved, named by the four things an admin needs to recognise them. */
	public record Student(String uniqueId, String name, String section, int rollNo) {
	}
}

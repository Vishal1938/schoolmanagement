package com.school.ai.api;

/**
 * A suggested report-card remark, two or three sentences long. <strong>Not saved.</strong>
 *
 * <p>{@code basis} is there so a teacher can see what the sentence was written from before they sign
 * their name under it — a remark about "improving in Science" is worth checking against the marks it
 * came from. It is the same summary that went into the prompt, which is deliberately narrow: a first
 * name, the subject marks, the movement since the last exam and the attendance percentage, and
 * nothing else about the child.
 */
public record ReportRemarkResponse(
		String studentUniqueId,
		String examId,
		String examName,
		String remark,
		Basis basis) {

	/**
	 * @param previousExamName null when this is the first published exam of the session, in which case
	 *                         there is no trend and the remark does not claim one
	 * @param trend            percentage points gained or lost since {@code previousExamName}, or null
	 *                         when there is nothing to compare against
	 * @param attendance       percentage of the session's marked days the student was present for
	 */
	public record Basis(
			double percentage,
			String grade,
			String previousExamName,
			Double trend,
			double attendance) {
	}
}

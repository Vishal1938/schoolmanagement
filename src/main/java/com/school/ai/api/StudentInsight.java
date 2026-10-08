package com.school.ai.api;

import java.util.List;

import com.school.ai.domain.InsightFlag;

/**
 * One student who needs looking at, and why.
 *
 * <p><strong>{@code flags} is the finding; {@code summary} is only its wording.</strong> Every flag
 * was computed in code from the school's records — see {@link InsightFlag} for the thresholds — and
 * the model was handed a first name and that list and asked for one plain sentence. It cannot add a
 * flag, drop one, or disagree with one. A client that would rather render its own wording should
 * read {@code flags} and ignore {@code summary} entirely.
 *
 * @param className null if the student's enrollment has no class, which B5 should not allow
 * @param summary   one line, or null when the provider could not be reached. A failure to phrase the
 *                  finding does not suppress the finding
 */
public record StudentInsight(
		String uniqueId,
		String name,
		String className,
		String section,
		List<InsightFlag> flags,
		String summary) {
}

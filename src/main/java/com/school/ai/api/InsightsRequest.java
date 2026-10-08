package com.school.ai.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * What to scan, for {@code POST /ai/insights}. Every field is optional; an empty body
 * ({@code {}}) scans the whole school.
 *
 * @param classId null for every class
 * @param section null for every section of {@code classId}; ignored when no class is named
 * @param limit   how many flagged students to return, 50 by default and 200 at the most. Applied
 *                <em>after</em> sorting by flag count, so the limit keeps the students who need
 *                attention most rather than whichever class was read first
 */
public record InsightsRequest(
		@Size(max = 64) String classId,
		@Size(max = 8) String section,
		@Min(1) @Max(200) Integer limit) {

	public int limitOrDefault() {
		return limit == null ? 50 : limit;
	}
}

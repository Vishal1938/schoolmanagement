package com.school.fees.api;

import java.time.Instant;
import java.util.List;

import com.school.fees.domain.Concession;
import com.school.fees.domain.ConcessionType;

/**
 * One concession.
 *
 * @param headIds empty means it applies to every head
 */
public record ConcessionResponse(
		String id,
		String studentUniqueId,
		String studentName,
		String sessionId,
		ConcessionType type,
		long value,
		List<String> headIds,
		String reason,
		Instant createdAt) {

	public static ConcessionResponse of(Concession concession, String studentName) {
		return new ConcessionResponse(concession.getId(), concession.getStudentUniqueId(), studentName,
				concession.getSessionId(), concession.getType(), concession.getValue(),
				concession.getHeadIds() == null ? List.of() : concession.getHeadIds(), concession.getReason(),
				concession.getCreatedAt());
	}
}

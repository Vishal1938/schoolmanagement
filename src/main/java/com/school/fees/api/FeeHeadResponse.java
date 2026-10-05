package com.school.fees.api;

import java.time.Instant;

import com.school.fees.domain.FeeHead;

/** One fee head. */
public record FeeHeadResponse(
		String id,
		String name,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {

	public static FeeHeadResponse of(FeeHead head) {
		return new FeeHeadResponse(head.getId(), head.getName(), head.isActive(), head.getCreatedAt(),
				head.getUpdatedAt());
	}
}

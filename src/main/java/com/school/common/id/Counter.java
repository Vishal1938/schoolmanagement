package com.school.common.id;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One monotonically increasing sequence. The document {@code _id} <em>is</em> the counter key — for
 * example {@code STU-26}, {@code EMP-26} or {@code DVM-RCP-26} — so a counter is created by the same
 * atomic upsert that increments it, and no separate initialisation step exists to forget.
 *
 * <p>Keys carry the two-digit year, so every January the sequence starts at 1 again for a new key.
 * That is safe because the year is part of the ID itself.
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(Counter.COLLECTION)
public class Counter {

	public static final String COLLECTION = "counters";

	@Id
	private String id;

	/** Last sequence handed out for this key. */
	private long seq;
}

package com.school.fees.domain;

import java.time.Instant;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * What one class pays in one session, split into installments.
 *
 * <p><strong>One structure per (session, class)</strong>, enforced by a unique index: "what does
 * Class 5 pay this year" has to have exactly one answer, or invoice generation would have to choose
 * between two of them.
 *
 * <p>Editing a structure does not rewrite invoices already generated from it. An invoice snapshots
 * its own items and amounts, so a correction here applies to invoices generated afterwards — except
 * for {@link #lateFine}, which is read live, because a fine is a running figure rather than a
 * recorded one.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(FeeStructure.COLLECTION)
@CompoundIndex(name = "fee_structures_session_class_idx", def = "{'sessionId': 1, 'classId': 1}",
		unique = true)
public class FeeStructure {

	public static final String COLLECTION = "fee_structures";

	@Id
	private String id;

	/** The academic session. Taken from AcademicContext, never from the client. */
	private String sessionId;

	private String classId;

	/** At least one, in the order the school collects them. */
	private List<Installment> installments;

	/** Null when the school charges nothing for paying late. */
	private LateFineRule lateFine;

	private Instant createdAt;

	private Instant updatedAt;

	/** What the whole year costs this class before any concession, in paise. */
	public long yearTotal() {
		return installments == null ? 0L : installments.stream().mapToLong(Installment::total).sum();
	}
}

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
 * A reduction granted to one student for one session — a staff ward, a sibling discount, a hardship
 * waiver.
 *
 * <p>Granted per session rather than open-endedly, because that is how a school reviews them: a
 * waiver for this year does not quietly follow a student into the next one.
 *
 * <p>A concession applies to invoices generated <strong>after</strong> it is granted. Invoices that
 * already exist are left alone until {@code POST /fees/concessions/{id}/apply} is called, so a
 * discount can never silently change a bill a family has already been shown.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(Concession.COLLECTION)
// Every read is "what does this student get this year?", for generation and for recalculation.
@CompoundIndex(name = "fee_concessions_student_session_idx",
		def = "{'studentUniqueId': 1, 'sessionId': 1}")
public class Concession {

	public static final String COLLECTION = "fee_concessions";

	@Id
	private String id;

	private String studentUniqueId;

	/** The session it was granted for. Taken from AcademicContext, never from the client. */
	private String sessionId;

	private ConcessionType type;

	/** A whole percentage for PERCENT, paise for FIXED. See {@link ConcessionType}. */
	private long value;

	/**
	 * Which heads it comes off. <strong>Empty means every head</strong> — a 100% staff-ward waiver is
	 * written once rather than listed head by head and then forgotten when a new head is added.
	 */
	private List<String> headIds;

	/** Why it was granted. Required: a discount with no stated reason is unauditable. */
	private String reason;

	private Instant createdAt;
}

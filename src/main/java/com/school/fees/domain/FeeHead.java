package com.school.fees.domain;

import java.time.Instant;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A thing a school charges for: Tuition, Transport, Exam, Admission.
 *
 * <p>Heads exist once for the school and are referenced by id from every structure and invoice, so
 * renaming "Bus" to "Transport" does not have to be repeated per class. Which heads a school has is
 * entirely its own business — none are hardcoded (CLAUDE.md rule 6).
 *
 * <p>Heads are never deleted: invoices already printed name them. A head that is no longer charged is
 * {@code active: false}, which keeps it off new structures while leaving old invoices readable.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(FeeHead.COLLECTION)
public class FeeHead {

	public static final String COLLECTION = "fee_heads";

	@Id
	private String id;

	/** Unique: two heads with one name would be indistinguishable on a receipt. */
	@Indexed(name = "fee_heads_name_idx", unique = true)
	private String name;

	/** False retires the head from new structures without touching the invoices that used it. */
	private boolean active;

	private Instant createdAt;

	private Instant updatedAt;
}

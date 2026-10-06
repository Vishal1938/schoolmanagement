package com.school.payroll.domain;

import java.time.Instant;
import java.time.LocalDate;
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
 * What one employee is paid, as agreed from {@link #effectiveFrom} onwards.
 *
 * <p><strong>Never overwritten.</strong> A raise, a new allowance or a corrected PF rate inserts a
 * fresh document with the next {@link #version}; the old one stays exactly as it was. That is not
 * tidiness — a salary slip printed last March has to stay reproducible, and "what was this person on
 * in April" is a question a school gets asked by auditors years later.
 *
 * <p>Which version a run uses is therefore a lookup by date, not "the latest one": see
 * {@code SalaryStructureService.effectiveFor}.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(SalaryStructure.COLLECTION)
// Two versions cannot share a number, which is what keeps the history a straight line even if two
// admins save a change at the same moment: the loser sees a duplicate key and retries.
@CompoundIndex(name = "salary_structures_employee_version_idx",
		def = "{'employeeUniqueId': 1, 'version': -1}", unique = true)
// The only read that matters: the version effective on a given date, newest first.
@CompoundIndex(name = "salary_structures_employee_effective_idx",
		def = "{'employeeUniqueId': 1, 'effectiveFrom': -1, 'version': -1}")
public class SalaryStructure {

	public static final String COLLECTION = "salary_structures";

	@Id
	private String id;

	/** The employee, by the id that never changes — not the Mongo document id. */
	private String employeeUniqueId;

	/** 1 for the first structure, then up by one per change. Unique per employee. */
	private int version;

	/** Basic pay in paise. The base for every PERCENT_OF_BASIC deduction. */
	private long basic;

	private List<SalaryComponent> allowances;

	private List<DeductionRule> deductions;

	/** The first day this version applies. A run picks the latest version on or before its month. */
	private LocalDate effectiveFrom;

	/** The admin who saved this version, by uniqueId. The audit trail has the rest. */
	private String createdBy;

	private Instant createdAt;

	/** Sum of the allowances, in paise. */
	public long allowanceTotal() {
		return allowances == null ? 0L : allowances.stream().mapToLong(SalaryComponent::amount).sum();
	}

	/** Basic plus allowances, in paise. What a full month earns before anything is withheld. */
	public long gross() {
		return basic + allowanceTotal();
	}
}

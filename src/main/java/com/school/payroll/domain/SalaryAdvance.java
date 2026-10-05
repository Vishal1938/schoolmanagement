package com.school.payroll.domain;

import java.time.Instant;
import java.time.LocalDate;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Money handed to an employee ahead of their salary, recovered a fixed amount per month.
 *
 * <p>{@link #recoveredSoFar} moves when a payroll record is <em>paid</em>, not when it is created: a
 * PENDING record is a plan, and a plan that gets deleted and re-run must not have taken anything off
 * the balance. The run still has to look at records it has already planned, or two pending months
 * would both deduct the same last installment — {@code PayrollService} does that when it works out
 * what is left to recover.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(SalaryAdvance.COLLECTION)
// The run's question: which advances is this employee still paying off, oldest first.
@CompoundIndex(name = "salary_advances_employee_status_idx",
		def = "{'employeeUniqueId': 1, 'status': 1, 'givenOn': 1}")
public class SalaryAdvance {

	public static final String COLLECTION = "salary_advances";

	@Id
	private String id;

	private String employeeUniqueId;

	/** Their name as it was when the advance was given, so a list needs no join. */
	private String employeeName;

	/** The whole advance, in paise. */
	private long amount;

	private LocalDate givenOn;

	/** Deducted from each payroll record until the advance is cleared, in paise. */
	private long monthlyRecovery;

	/** Recovered through paid salaries so far, in paise. Never more than {@link #amount}. */
	private long recoveredSoFar;

	private AdvanceStatus status;

	/** Why it was given, in the school's own words. Printed nowhere; it is for the office. */
	private String reason;

	private Instant createdAt;

	private Instant updatedAt;

	/** What is still owed, in paise. */
	public long remaining() {
		return Math.max(0L, amount - recoveredSoFar);
	}
}

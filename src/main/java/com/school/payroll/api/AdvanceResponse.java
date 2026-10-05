package com.school.payroll.api;

import java.time.LocalDate;

import com.school.payroll.domain.AdvanceStatus;
import com.school.payroll.domain.SalaryAdvance;

/**
 * One advance and where its recovery stands.
 *
 * @param remaining what is still owed, in paise. Moves when a salary is paid, not when a run plans it
 */
public record AdvanceResponse(
		String id,
		String employeeUniqueId,
		String employeeName,
		long amount,
		LocalDate givenOn,
		long monthlyRecovery,
		long recoveredSoFar,
		long remaining,
		AdvanceStatus status,
		String reason) {

	public static AdvanceResponse of(SalaryAdvance advance) {
		return new AdvanceResponse(
				advance.getId(),
				advance.getEmployeeUniqueId(),
				advance.getEmployeeName(),
				advance.getAmount(),
				advance.getGivenOn(),
				advance.getMonthlyRecovery(),
				advance.getRecoveredSoFar(),
				advance.remaining(),
				advance.getStatus(),
				advance.getReason());
	}
}

package com.school.payroll.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.school.payroll.domain.DeductionRule;
import com.school.payroll.domain.SalaryComponent;
import com.school.payroll.domain.SalaryStructure;

/**
 * {@code GET /payroll/structures/{employeeUniqueId}}: what this employee is on now, and every
 * version it has ever been.
 *
 * @param current the version in force <em>today</em>, or null when the only versions there are start
 *                in the future — a structure saved in advance of a raise. A payroll run asks the same
 *                question against the month it is running, not against today
 * @param history every version, newest first, including any future-dated one
 */
public record SalaryStructureResponse(
		String employeeUniqueId,
		String employeeName,
		Version current,
		List<Version> history) {

	/**
	 * One version of the structure. {@code gross} is basic plus allowances; it is not what anybody
	 * takes home, which depends on the month — see the payroll record for that.
	 */
	public record Version(
			String id,
			int version,
			long basic,
			List<SalaryComponentView> allowances,
			List<DeductionView> deductions,
			long allowanceTotal,
			long deductionTotal,
			long gross,
			LocalDate effectiveFrom,
			String createdBy,
			Instant createdAt) {

		public static Version of(SalaryStructure structure) {
			List<DeductionView> deductions = (structure.getDeductions() == null
					? List.<DeductionRule>of()
					: structure.getDeductions()).stream()
					.map(rule -> DeductionView.of(rule, structure.getBasic()))
					.toList();
			return new Version(
					structure.getId(),
					structure.getVersion(),
					structure.getBasic(),
					(structure.getAllowances() == null ? List.<SalaryComponent>of() : structure.getAllowances())
							.stream()
							.map(SalaryComponentView::of)
							.toList(),
					deductions,
					structure.allowanceTotal(),
					deductions.stream().mapToLong(DeductionView::amount).sum(),
					structure.gross(),
					structure.getEffectiveFrom(),
					structure.getCreatedBy(),
					structure.getCreatedAt());
		}
	}
}

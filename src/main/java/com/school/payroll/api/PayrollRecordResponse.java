package com.school.payroll.api;

import java.time.LocalDate;
import java.util.List;

import com.school.payroll.domain.AppliedDeduction;
import com.school.payroll.domain.PayrollRecord;
import com.school.payroll.domain.PayrollStatus;
import com.school.payroll.domain.SalaryComponent;
import com.school.payroll.domain.SalaryPaymentMode;

/**
 * One employee's salary for one month, exactly as it was computed.
 *
 * <p>Every figure is a stored snapshot rather than something worked out on read, so this is what the
 * slip prints and what the school decided that month — a later raise does not change it.
 *
 * @param net      {@code gross - deductionTotal - advanceRecovery - lossOfPay}, floored at zero
 * @param slipUrl  where to fetch the salary slip PDF
 */
public record PayrollRecordResponse(
		String id,
		String month,
		String employeeUniqueId,
		String employeeName,
		String employeeType,
		String designation,
		int structureVersion,
		long basic,
		List<SalaryComponentView> allowances,
		long gross,
		List<DeductionView> deductions,
		long deductionTotal,
		long advanceRecovery,
		boolean prorated,
		int daysInMonth,
		int absentDays,
		int halfDays,
		long lossOfPay,
		long net,
		PayrollStatus status,
		LocalDate paidOn,
		SalaryPaymentMode mode,
		String reference,
		String slipUrl) {

	public static PayrollRecordResponse of(PayrollRecord record, String slipUrl) {
		return new PayrollRecordResponse(
				record.getId(),
				record.getMonth(),
				record.getEmployeeUniqueId(),
				record.getEmployeeName(),
				record.getEmployeeType(),
				record.getDesignation(),
				record.getStructureVersion(),
				record.getBasic(),
				(record.getAllowances() == null ? List.<SalaryComponent>of() : record.getAllowances()).stream()
						.map(SalaryComponentView::of)
						.toList(),
				record.getGross(),
				(record.getDeductions() == null ? List.<AppliedDeduction>of() : record.getDeductions()).stream()
						.map(DeductionView::of)
						.toList(),
				record.getDeductionTotal(),
				record.getAdvanceRecovery(),
				record.isProrated(),
				record.getDaysInMonth(),
				record.getAbsentDays(),
				record.getHalfDays(),
				record.getLossOfPay(),
				record.getNet(),
				record.getStatus(),
				record.getPaidOn(),
				record.getMode(),
				record.getReference(),
				slipUrl);
	}
}

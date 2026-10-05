package com.school.payroll.api;

import java.time.LocalDate;

import com.school.payroll.domain.SalaryPaymentMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /payroll/records/{id}/pay}: the salary has gone out.
 *
 * @param reference the UTR, cheque number or UPI reference. Optional, because cash has none
 * @param paidOn    the day the money left; defaults to today, and may not be in the future
 */
public record PayRecordRequest(
		@NotNull SalaryPaymentMode mode,
		@Size(max = 60) String reference,
		LocalDate paidOn) {
}

package com.school.payroll.api;

import java.time.LocalDate;
import java.util.List;

import com.school.payroll.domain.SalaryPaymentMode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /payroll/records/pay-bulk}: one bank run, many salaries.
 *
 * <p>There is no per-record reference here — that is the point of the endpoint. Pay one record at a
 * time through {@code /payroll/records/{id}/pay} when each needs its own UTR or cheque number.
 *
 * @param paidOn defaults to today, and may not be in the future
 */
public record PayBulkRequest(
		@NotEmpty @Size(max = 500) List<@NotNull String> recordIds,
		@NotNull SalaryPaymentMode mode,
		LocalDate paidOn) {
}

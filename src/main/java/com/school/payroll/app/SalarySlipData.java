package com.school.payroll.app;

import com.school.payroll.domain.PayrollRecord;
import com.school.schoolconfig.domain.ContactDetails;
import com.school.schoolconfig.domain.Identity;

/**
 * Everything one salary slip prints, gathered before a single byte of PDF is written.
 *
 * <p>{@link SalarySlipPdf} takes this and nothing else — it reads no database and calls no service —
 * so who may see a slip is settled in {@link SalarySlipService}, where the authorization is, and not
 * somewhere inside a layout routine.
 *
 * @param monthLabel the month spelled out, e.g. "October 2026"
 * @param footer     the configured salary-slip footer; may be null, and the slip is never refused
 *                   over it
 */
public record SalarySlipData(
		Identity identity,
		ContactDetails contact,
		PayrollRecord record,
		String monthLabel,
		String footer) {
}

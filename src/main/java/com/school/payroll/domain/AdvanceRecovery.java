package com.school.payroll.domain;

/**
 * One advance's installment inside one payroll record.
 *
 * <p>A list rather than a single advance id, because an employee can be paying off more than one at
 * a time, and paying the record has to know which balances to move and by how much.
 *
 * @param amount the installment planned for this month, in paise
 */
public record AdvanceRecovery(String advanceId, long amount) {
}

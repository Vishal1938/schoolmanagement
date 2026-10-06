package com.school.schoolconfig.domain;

import java.time.DayOfWeek;
import java.util.Set;

/**
 * Operational settings used across the modules. None of this is public-safe: it is what the school
 * runs on, not what it advertises.
 *
 * @param workingDays               days attendance is expected on (B8)
 * @param attendanceEditWindowHours how long after marking a teacher may still correct attendance;
 *                                  {@code 0} means no edits after submission (B8)
 * @param receiptPrefix             prefix of receipt numbers for fee payments (B12)
 * @param receiptFooter             text printed at the bottom of every receipt PDF
 * @param salarySlipFooter          text printed at the bottom of every salary slip PDF (B13)
 * @param reportCardFooter          text printed at the bottom of every report card PDF (B9)
 */
public record AcademicSettings(
		Set<DayOfWeek> workingDays,
		int attendanceEditWindowHours,
		String receiptPrefix,
		String receiptFooter,
		String salarySlipFooter,
		String reportCardFooter) {
}

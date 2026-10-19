package com.school.attendance.app;

import java.time.LocalDate;

/**
 * One day's student attendance for the whole institution, as the admin dashboard reads it.
 *
 * <p>Deliberately a tally and nothing finer — no class breakdown and no names — so the dashboard
 * cannot become a second route to a register. Whoever wants the detail opens the register.
 *
 * <p><strong>{@code sectionsMarked} is the figure to read first.</strong> A percentage over three of
 * thirty sections is not the school's attendance, it is three teachers who have submitted so far, and
 * the only honest way to present it is next to how many registers it came from.
 *
 * @param date           the day these figures are for
 * @param sectionsMarked registers actually submitted for that day
 * @param sectionsTotal  class-sections that exist, i.e. how many registers there would be if every
 *                       teacher had submitted
 * @param markedStudents the denominator of {@code percentage}: students marked, excluding LEAVE
 * @param percentage     {@code (present + late + 0.5 × halfDay) / markedStudents × 100}, to two
 *                       decimals, and {@code 0} when nothing was marked
 */
public record DayAttendance(
		LocalDate date,
		int sectionsMarked,
		int sectionsTotal,
		int present,
		int absent,
		int late,
		int halfDay,
		int leave,
		int markedStudents,
		double percentage) {
}

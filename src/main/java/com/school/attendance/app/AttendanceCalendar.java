package com.school.attendance.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import com.school.common.security.CurrentUser;
import com.school.common.security.Permission;
import com.school.schoolconfig.app.SchoolConfigService;
import org.springframework.stereotype.Service;

/**
 * The one place that decides whether a given date may be marked, and by whom.
 *
 * <p>Both registers ask the same four questions, in this order, because the answers get more
 * specific and a vaguer reason is more useful than a precise one about the wrong thing:
 *
 * <ol>
 * <li><strong>Not in the future.</strong> Attendance is a record of what happened.</li>
 * <li><strong>A working day</strong>, per {@code academicSettings.workingDays}.</li>
 * <li><strong>Not a holiday.</strong></li>
 * <li><strong>Within the edit window</strong>, which only binds a teacher.</li>
 * </ol>
 *
 * <p>The window — {@code academicSettings.attendanceEditWindowHours} — runs from the moment the
 * register was last submitted, which is what makes it a correction window. A register that has
 * never been submitted has no such moment, so for a first submission it runs from the start of the
 * day being marked instead: with the seeded 48 hours, a teacher has until the end of the day after
 * to get Monday's register in, and then two days from each correction to fix it. An admin is never
 * bound by it, which is the escape hatch for everything this cannot anticipate.
 */
@Service
public class AttendanceCalendar {

	private final SchoolConfigService schoolConfig;
	private final HolidayService holidays;
	private final Clock clock;

	public AttendanceCalendar(SchoolConfigService schoolConfig, HolidayService holidays, Clock clock) {
		this.schoolConfig = schoolConfig;
		this.holidays = holidays;
		this.clock = clock;
	}

	/**
	 * @param markedAt when the register was last submitted, or null if it never has been
	 * @return whether the caller may write this register now, and why not if they may not
	 */
	Markability check(LocalDate date, Instant markedAt) {
		LocalDate today = LocalDate.now(clock);
		if (date.isAfter(today)) {
			return Markability.refused("Attendance cannot be marked for a future date");
		}
		if (!schoolConfig.workingDays().contains(date.getDayOfWeek())) {
			String dayName = date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
			return Markability.refused(dayName + " is not a working day at this school");
		}
		if (holidays.isHoliday(date)) {
			return Markability.refused(date + " is a holiday");
		}
		// An admin may correct any past date; the window exists to stop a teacher quietly rewriting
		// last term, not to stop the office fixing a mistake.
		if (CurrentUser.require().permissions().contains(Permission.ATTENDANCE_CORRECT_ANY)) {
			return Markability.permitted();
		}

		int windowHours = schoolConfig.attendanceEditWindowHours();
		Instant from = markedAt != null ? markedAt : date.atStartOfDay(clock.getZone()).toInstant();
		Instant deadline = from.plus(windowHours, ChronoUnit.HOURS);
		if (Instant.now(clock).isAfter(deadline)) {
			return Markability.refused(markedAt == null
					? "The " + windowHours + "-hour window for marking " + date + " has closed. "
							+ "Ask an administrator to mark it."
					: "This register was submitted on " + markedAt + " and the " + windowHours
							+ "-hour correction window has closed. Ask an administrator to change it.");
		}
		return Markability.permitted();
	}
}

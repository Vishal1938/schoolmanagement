package com.school.attendance.app;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.school.attendance.domain.EmployeeAttendance;
import com.school.attendance.domain.Holiday;
import com.school.attendance.infra.EmployeeAttendanceRepository;
import com.school.attendance.infra.HolidayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Wipes the staff registers and the holidays of a date range, for the demonstration data reset (B20).
 *
 * <p>Here rather than in the demonstration module because the repositories are this module's.
 *
 * <p><strong>Only the employee registers go.</strong> The student registers are left alone even
 * inside the range: nothing in the demonstration data set writes one — a college typically has
 * {@code studentAttendance} switched off — so deleting them could only destroy something a person
 * entered by hand.
 */
@Service
public class AttendanceDataResetService {

	private static final Logger log = LoggerFactory.getLogger(AttendanceDataResetService.class);

	private final EmployeeAttendanceRepository attendance;
	private final HolidayRepository holidays;

	public AttendanceDataResetService(EmployeeAttendanceRepository attendance, HolidayRepository holidays) {
		this.attendance = attendance;
		this.holidays = holidays;
	}

	/**
	 * Removes every staff register and every holiday between these dates, both ends inclusive.
	 *
	 * <p>Not transactional: a half-finished wipe is safe, because running it again removes what is
	 * still there.
	 */
	public AttendanceDataPurge purge(LocalDate from, LocalDate to) {
		List<EmployeeAttendance> registers = attendance.findByDateBetweenOrderByDateAsc(from, to);
		List<Holiday> declared = holidays.findByDateBetweenOrderByDateAsc(from, to);

		attendance.deleteAll(registers);
		holidays.deleteAll(declared);

		AttendanceDataPurge purge = new AttendanceDataPurge(registers.size(), declared.size());
		log.info("Wiped staff attendance and holidays from {} to {}: {}", from, to, purge);
		return purge;
	}

	/** What a wipe removed, per collection. */
	public record AttendanceDataPurge(int employeeAttendanceDays, int holidays) {

		/** For the response and the audit entry. */
		public Map<String, Integer> asCounts() {
			return Map.of("employeeAttendanceDays", employeeAttendanceDays, "holidays", holidays);
		}
	}
}

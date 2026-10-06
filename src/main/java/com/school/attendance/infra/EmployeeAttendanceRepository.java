package com.school.attendance.infra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.school.attendance.domain.EmployeeAttendance;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for {@code employee_attendance}. Internal to this module; callers use
 * {@code EmployeeAttendanceService}.
 */
public interface EmployeeAttendanceRepository extends MongoRepository<EmployeeAttendance, String> {

	Optional<EmployeeAttendance> findByDate(LocalDate date);

	List<EmployeeAttendance> findByEntriesEmployeeUniqueIdAndDateBetweenOrderByDateAsc(
			String employeeUniqueId, LocalDate from, LocalDate to);
}

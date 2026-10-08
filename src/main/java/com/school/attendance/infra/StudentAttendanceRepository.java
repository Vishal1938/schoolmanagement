package com.school.attendance.infra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.school.attendance.domain.StudentAttendance;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for {@code student_attendance}. Internal to this module; callers use
 * {@code StudentAttendanceService}.
 */
public interface StudentAttendanceRepository extends MongoRepository<StudentAttendance, String> {

	Optional<StudentAttendance> findByClassIdAndSectionAndDate(String classId, String section, LocalDate date);

	/**
	 * Every register in the range that mentions this student. Reaching into the array is what the
	 * {@code entries.studentUniqueId} index is there for; the caller picks their entry out.
	 */
	List<StudentAttendance> findByEntriesStudentUniqueIdAndDateBetweenOrderByDateAsc(
			String studentUniqueId, LocalDate from, LocalDate to);

	/**
	 * Every register of a class in the range, all sections. Served by the {@code classId} prefix of
	 * the class-section-date index. For the whole-class percentages in B18, which would otherwise be
	 * one query per child.
	 */
	List<StudentAttendance> findByClassIdAndDateBetween(String classId, LocalDate from, LocalDate to);
}

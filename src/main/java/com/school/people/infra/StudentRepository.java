package com.school.people.infra;

import java.util.List;
import java.util.Optional;

import com.school.people.domain.Student;
import com.school.people.domain.StudentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for {@code students}. Internal to this module: everybody else goes through
 * {@code StudentService}.
 *
 * <p>The list query is not here. It combines four optional filters with a three-way {@code ?q=}
 * search, which a derived method cannot express, so {@code StudentService} builds it with
 * {@code MongoOperations}.
 */
public interface StudentRepository extends MongoRepository<Student, String> {

	Optional<Student> findByUniqueId(String uniqueId);

	/** The register of a class-section, in the order it is read out. Backs attendance (B8). */
	List<Student> findByEnrollmentClassIdAndEnrollmentSectionAndStatusOrderByEnrollmentRollNoAsc(
			String classId, String section, StudentStatus status);

	/** A whole class across its sections, for one session. Backs fee invoice generation (B11). */
	List<Student> findByEnrollmentSessionIdAndEnrollmentClassIdAndStatusOrderByEnrollmentSectionAscEnrollmentRollNoAsc(
			String sessionId, String classId, StudentStatus status);

	boolean existsByAdmissionNo(String admissionNo);

	boolean existsByAdmissionNoAndIdNot(String admissionNo, String id);

	/** Guards the roll number against being issued twice in one section. */
	boolean existsByEnrollmentSessionIdAndEnrollmentClassIdAndEnrollmentSectionAndEnrollmentRollNo(
			String sessionId, String classId, String section, int rollNo);

	boolean existsByEnrollmentSessionIdAndEnrollmentClassIdAndEnrollmentSectionAndEnrollmentRollNoAndIdNot(
			String sessionId, String classId, String section, int rollNo, String id);
}

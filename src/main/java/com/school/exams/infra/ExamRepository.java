package com.school.exams.infra;

import java.util.List;

import com.school.exams.domain.Exam;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Repository for {@code exams}. Internal to this module; callers use {@code ExamService}. */
public interface ExamRepository extends MongoRepository<Exam, String> {

	List<Exam> findBySessionIdOrderByNameAsc(String sessionId);

	/** {@code classIds} is an array, so this matches any exam that includes the class. */
	List<Exam> findBySessionIdAndClassIdsContainsOrderByNameAsc(String sessionId, String classId);

	boolean existsByNameAndSessionId(String name, String sessionId);

	boolean existsByNameAndSessionIdAndIdNot(String name, String sessionId, String id);
}

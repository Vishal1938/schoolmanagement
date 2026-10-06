package com.school.exams.infra;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.school.exams.domain.Mark;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Repository for {@code marks}. Internal to this module; callers use {@code MarksService}. */
public interface MarkRepository extends MongoRepository<Mark, String> {

	Optional<Mark> findByExamIdAndStudentUniqueIdAndSubjectId(String examId, String studentUniqueId, String subjectId);

	/** One subject's column, for the marks-entry grid. */
	List<Mark> findByExamIdAndSubjectIdAndStudentUniqueIdIn(String examId, String subjectId,
			Collection<String> studentUniqueIds);

	/** One student's row across every subject, for a report card. */
	List<Mark> findByExamIdAndStudentUniqueId(String examId, String studentUniqueId);

	/** A whole class-section's marks in one read, for the result sheet and the rank. */
	List<Mark> findByExamIdAndStudentUniqueIdIn(String examId, Collection<String> studentUniqueIds);

	List<Mark> findByExamId(String examId);
}

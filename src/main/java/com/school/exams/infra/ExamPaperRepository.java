package com.school.exams.infra;

import java.util.List;
import java.util.Optional;

import com.school.exams.domain.ExamPaper;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Repository for {@code exam_papers}. Internal to this module; callers use {@code ExamPaperService}. */
public interface ExamPaperRepository extends MongoRepository<ExamPaper, String> {

	/** The one paper for a triple, which a second upload turns into a new version. */
	Optional<ExamPaper> findByExamIdAndClassIdAndSubjectId(String examId, String classId, String subjectId);

	List<ExamPaper> findByExamIdAndClassIdOrderByTitleAsc(String examId, String classId);

	List<ExamPaper> findByExamIdOrderByTitleAsc(String examId);

	List<ExamPaper> findByClassIdOrderByTitleAsc(String classId);

	List<ExamPaper> findAllByOrderByTitleAsc();
}

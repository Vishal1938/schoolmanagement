package com.school.quiz.infra;

import java.util.List;

import com.school.quiz.domain.Quiz;
import com.school.quiz.domain.QuizStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for {@code quizzes}. Internal to this module; callers use {@code QuizService}.
 *
 * <p>The teacher's and admin's list is filtered on an optional class and an optional status, which is
 * four combinations rather than four derived queries, so {@code QuizService} assembles that one with
 * {@code MongoOperations}. What is here is the students' list, whose shape never varies.
 */
public interface QuizRepository extends MongoRepository<Quiz, String> {

	/**
	 * Every quiz in one status set for one class, soonest first. The students' list; the section filter
	 * and the window are applied in the service, because "no sections" means "all of them" and is not
	 * expressible as a query on the stored array.
	 */
	List<Quiz> findByClassIdAndStatusOrderByStartAtAsc(String classId, QuizStatus status);
}

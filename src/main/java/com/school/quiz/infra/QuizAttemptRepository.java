package com.school.quiz.infra;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.school.quiz.domain.QuizAttempt;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Repository for {@code quiz_attempts}. Internal to this module. */
public interface QuizAttemptRepository extends MongoRepository<QuizAttempt, String> {

	/** One student's attempts at one quiz, oldest first: the count that enforces {@code maxAttempts}. */
	List<QuizAttempt> findByQuizIdAndStudentUniqueIdOrderByAttemptNoAsc(String quizId, String studentUniqueId);

	/** Everybody's attempts at one quiz, for the results sheet. */
	List<QuizAttempt> findByQuizId(String quizId);

	/** Every attempt by one student, newest first. {@code GET /quizzes/attempts/mine}. */
	List<QuizAttempt> findByStudentUniqueIdOrderByStartedAtDesc(String studentUniqueId);

	/**
	 * The latest attempt a student has in flight, if any, so a refresh resumes instead of starting
	 * again.
	 *
	 * <p>{@code findFirst ... OrderByAttemptNoDesc} rather than a plain finder: an attempt abandoned
	 * past its deadline also has no {@code submittedAt} until the sweeper gets to it, so a student can
	 * legitimately have more than one of these and a single-result query would blow up on the second.
	 */
	Optional<QuizAttempt> findFirstByQuizIdAndStudentUniqueIdAndSubmittedAtIsNullOrderByAttemptNoDesc(
			String quizId, String studentUniqueId);

	/**
	 * In-flight attempts whose deadline has passed, oldest first. The sweeper's query; capped so one
	 * pass cannot be unbounded, and the next minute's run picks up whatever is left.
	 */
	List<QuizAttempt> findTop200BySubmittedAtIsNullAndDeadlineLessThanOrderByDeadlineAsc(Instant cutoff);

	/** Every attempt at a quiz, counted — used to refuse deleting a quiz that has been sat. */
	long countByQuizId(String quizId);
}

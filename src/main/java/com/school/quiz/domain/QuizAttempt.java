package com.school.quiz.domain;

import java.time.Instant;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One student's sitting of one quiz.
 *
 * <p>Created the moment the questions are handed over, not when they come back. That is what makes
 * the time limit enforceable on the server and what makes an abandoned attempt count against
 * {@code maxAttempts} — a student cannot read the paper, close the tab and start again fresh.
 *
 * <p>{@link #deadline} is fixed at creation as the earlier of "started plus the time limit" and the
 * quiz's {@code endAt}, and is never recomputed. {@link #submittedAt} null means still in flight: such
 * an attempt is resumable until its deadline passes, after which the sweeper submits it empty.
 *
 * <p>{@link #maxScore} is a snapshot of the quiz's total marks taken at creation. Denormalised so an
 * attempt can be shown, or auto-submitted, without reading the quiz back.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(QuizAttempt.COLLECTION)
// One student's attempts at one quiz: the count that enforces maxAttempts, and the results listing.
// Unique on the attempt number, so two concurrent starts cannot both become attempt 2.
@CompoundIndex(name = "quiz_attempts_unique_idx",
		def = "{'quizId': 1, 'studentUniqueId': 1, 'attemptNo': 1}", unique = true)
// "My attempts", newest first.
@CompoundIndex(name = "quiz_attempts_student_idx", def = "{'studentUniqueId': 1, 'startedAt': -1}")
// The sweeper's query: still in flight, past its deadline.
@CompoundIndex(name = "quiz_attempts_open_idx", def = "{'submittedAt': 1, 'deadline': 1}")
public class QuizAttempt {

	public static final String COLLECTION = "quiz_attempts";

	@Id
	private String id;

	private String quizId;

	/** Copied so the results list can be built without re-reading every student record. */
	private String quizTitle;

	private String studentUniqueId;

	private String studentName;

	private String classId;

	private String section;

	/** 1-based, and unique per student per quiz. */
	private int attemptNo;

	private Instant startedAt;

	/** The server's last word on when this attempt ends. See the class comment. */
	private Instant deadline;

	/** Null while the attempt is still in flight. */
	private Instant submittedAt;

	/**
	 * True when the sweeper submitted it rather than the student. Such an attempt has no answers and
	 * scores zero; it is kept, and counted, because the student did take the paper away.
	 */
	private boolean autoSubmitted;

	/**
	 * The order the questions were presented in, by id. Stored so a student who refreshes mid-attempt
	 * gets the same paper back rather than a freshly shuffled one.
	 */
	private List<String> questionOrder;

	/** Empty until submitted. One entry per question the student answered. */
	private List<AttemptAnswer> answers;

	private int score;

	/** The quiz's total marks as they were when this attempt started. */
	private int maxScore;

	public boolean isSubmitted() {
		return submittedAt != null;
	}
}

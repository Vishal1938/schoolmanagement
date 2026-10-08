package com.school.quiz.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.school.common.exceptions.BusinessRuleException;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ForbiddenException;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ValidationException;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.CurrentUser;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import com.school.quiz.api.AttemptResultResponse;
import com.school.quiz.api.AttemptStartResponse;
import com.school.quiz.api.AvailableQuizResponse;
import com.school.quiz.api.MyAttemptResponse;
import com.school.quiz.api.SubmitAttemptRequest;
import com.school.quiz.domain.AttemptAnswer;
import com.school.quiz.domain.QuestionOption;
import com.school.quiz.domain.QuestionType;
import com.school.quiz.domain.Quiz;
import com.school.quiz.domain.QuizAttempt;
import com.school.quiz.domain.QuizQuestion;
import com.school.quiz.domain.QuizStatus;
import com.school.quiz.domain.QuizWindow;
import com.school.quiz.infra.QuizAttemptRepository;
import com.school.quiz.infra.QuizRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Quizzes as a student sits them: what is available, starting an attempt, submitting it, and the
 * history.
 *
 * <p><strong>The clock is the server's.</strong> An attempt's deadline is written when the questions
 * are handed over and never recomputed, and a submission is checked against that stored value. The
 * client's countdown is a courtesy; nothing it sends about time is read.
 *
 * <p><strong>The answer key never reaches a student before they have submitted.</strong> Starting an
 * attempt returns {@link AttemptStartResponse}, a record with no field that could hold a key, and the
 * review in {@link AttemptResultResponse} is withheld entirely unless the quiz has
 * {@code showAnswersAfterSubmit}.
 *
 * <p>Attempts are not written to {@code audit_logs}. The attempt document <em>is</em> the record —
 * who, which quiz, when it started, when it came back and what it scored — and a second copy of every
 * answer a school's students ever gave would swamp a trail whose purpose is marks, money and papers.
 */
@Service
public class QuizAttemptService {

	/**
	 * How long past the deadline a submission is still taken. Covers the round trip of an answer sent
	 * at the last second, a clock a little behind the server's, and a slow mobile connection — without
	 * it, a student who answers on time can still lose the paper.
	 *
	 * <p>{@code ExpiredAttemptSweeper} waits out the same grace before writing an attempt off, so the
	 * two can never race over one paper.
	 */
	static final Duration SUBMIT_GRACE = Duration.ofSeconds(30);

	private final QuizRepository quizzes;
	private final QuizAttemptRepository attempts;
	private final StudentService students;
	private final Clock clock;

	public QuizAttemptService(QuizRepository quizzes, QuizAttemptRepository attempts, StudentService students,
			Clock clock) {
		this.quizzes = quizzes;
		this.attempts = attempts;
		this.students = students;
		this.clock = clock;
	}

	// --- what is available ------------------------------------------------------------------------

	/**
	 * The published quizzes set for this student's class and section, soonest first, each with where
	 * the clock has got to and what they have already scored. <strong>No questions</strong> — see
	 * {@link AvailableQuizResponse}.
	 *
	 * <p>A student with no enrollment gets an empty list rather than an error: a newly admitted record
	 * without a class is a normal state, not a failed request.
	 */
	public List<AvailableQuizResponse> available() {
		StudentRef student = requireStudent();
		if (student.classId() == null) {
			return List.of();
		}

		Instant now = Instant.now(clock);
		Map<String, List<QuizAttempt>> mine = attemptsByQuiz(student.uniqueId());
		return quizzes.findByClassIdAndStatusOrderByStartAtAsc(student.classId(), QuizStatus.PUBLISHED).stream()
				.filter(quiz -> quiz.coversSection(student.section()))
				.map(quiz -> toAvailable(quiz, mine.getOrDefault(quiz.getId(), List.of()), now))
				.toList();
	}

	private AvailableQuizResponse toAvailable(Quiz quiz, List<QuizAttempt> mine, Instant now) {
		QuizWindow window = QuizWindow.at(now, quiz.getStartAt(), quiz.getEndAt());
		boolean inProgress = mine.stream()
				.anyMatch(attempt -> !attempt.isSubmitted() && attempt.getDeadline().isAfter(now));
		Optional<Integer> best = mine.stream()
				.filter(QuizAttempt::isSubmitted)
				.map(QuizAttempt::getScore)
				.max(Comparator.naturalOrder());

		return new AvailableQuizResponse(
				quiz.getId(),
				quiz.getTitle(),
				quiz.getDescription(),
				quiz.getSubjectId(),
				quiz.getClassId(),
				quiz.getTimeLimitMinutes(),
				quiz.getStartAt(),
				quiz.getEndAt(),
				quiz.getMaxAttempts(),
				quiz.questionCount(),
				quiz.totalMarks(),
				window,
				mine.size(),
				best.orElse(null),
				window == QuizWindow.OPEN && (inProgress || mine.size() < quiz.getMaxAttempts()),
				inProgress);
	}

	private Map<String, List<QuizAttempt>> attemptsByQuiz(String studentUniqueId) {
		Map<String, List<QuizAttempt>> byQuiz = new HashMap<>();
		for (QuizAttempt attempt : attempts.findByStudentUniqueIdOrderByStartedAtDesc(studentUniqueId)) {
			byQuiz.computeIfAbsent(attempt.getQuizId(), key -> new ArrayList<>()).add(attempt);
		}
		return byQuiz;
	}

	// --- starting an attempt ----------------------------------------------------------------------

	/**
	 * Hands over the paper and starts the clock.
	 *
	 * <p><strong>An attempt still in flight is returned instead of a new one.</strong> A student who
	 * refreshes the page, or whose phone drops the connection, gets the same paper back in the same
	 * order with the original deadline — not a second attempt off their allowance, and not a reset
	 * timer. That is the whole reason the question order is stored on the attempt.
	 */
	public AttemptStartResponse start(String quizId) {
		StudentRef student = requireStudent();
		Quiz quiz = requireSittable(quizId, student);
		Instant now = Instant.now(clock);

		QuizWindow window = QuizWindow.at(now, quiz.getStartAt(), quiz.getEndAt());
		if (window == QuizWindow.UPCOMING) {
			throw new BusinessRuleException("This quiz is not open yet; it opens at " + quiz.getStartAt());
		}
		if (window == QuizWindow.ENDED) {
			throw new BusinessRuleException("This quiz closed at " + quiz.getEndAt());
		}

		Optional<QuizAttempt> inFlight = attempts
				.findFirstByQuizIdAndStudentUniqueIdAndSubmittedAtIsNullOrderByAttemptNoDesc(
						quizId, student.uniqueId());
		if (inFlight.isPresent() && inFlight.get().getDeadline().isAfter(now)) {
			return toStartResponse(quiz, inFlight.get(), true);
		}
		// An in-flight attempt past its deadline is spent, not resumable: it still counts below, and the
		// sweeper will write it off as a zero.

		int used = attempts.findByQuizIdAndStudentUniqueIdOrderByAttemptNoAsc(quizId, student.uniqueId()).size();
		if (used >= quiz.getMaxAttempts()) {
			throw new BusinessRuleException("You have used all " + quiz.getMaxAttempts()
					+ (quiz.getMaxAttempts() == 1 ? " attempt" : " attempts") + " at this quiz");
		}

		QuizAttempt attempt = QuizAttempt.builder()
				.quizId(quiz.getId())
				.quizTitle(quiz.getTitle())
				.studentUniqueId(student.uniqueId())
				.studentName(student.name())
				.classId(student.classId())
				.section(student.section())
				.attemptNo(used + 1)
				.startedAt(now)
				.deadline(deadline(quiz, now))
				.questionOrder(questionOrder(quiz))
				.answers(List.of())
				.score(0)
				.maxScore(quiz.totalMarks())
				.build();

		try {
			return toStartResponse(quiz, attempts.insert(attempt), false);
		}
		catch (DuplicateKeyException ex) {
			// Two starts landed at once and the unique index on (quiz, student, attemptNo) caught the
			// second. Better a retryable 409 than two attempts numbered the same.
			throw new ConflictException("Another attempt at this quiz was started at the same moment. "
					+ "Reload the page and try again.", ex);
		}
	}

	/**
	 * The earlier of "now plus the time limit" and the quiz's own closing time. A student who starts
	 * five minutes before the quiz closes gets five minutes, not the full limit — otherwise the window
	 * would mean nothing.
	 */
	private static Instant deadline(Quiz quiz, Instant startedAt) {
		Instant limit = startedAt.plus(Duration.ofMinutes(quiz.getTimeLimitMinutes()));
		return limit.isBefore(quiz.getEndAt()) ? limit : quiz.getEndAt();
	}

	/** This attempt's presentation order: the quiz's own, or its own shuffle when the quiz says so. */
	private static List<String> questionOrder(Quiz quiz) {
		List<String> ids = new ArrayList<>(quiz.getQuestions().stream().map(QuizQuestion::id).toList());
		if (quiz.isShuffleQuestions()) {
			Collections.shuffle(ids);
		}
		return List.copyOf(ids);
	}

	private static AttemptStartResponse toStartResponse(Quiz quiz, QuizAttempt attempt, boolean resumed) {
		Map<String, QuizQuestion> byId = questionsById(quiz);
		List<AttemptStartResponse.Question> questions = attempt.getQuestionOrder().stream()
				.map(byId::get)
				.filter(question -> question != null)
				.map(question -> new AttemptStartResponse.Question(
						question.id(),
						question.type(),
						question.text(),
						question.options().stream()
								.map(option -> new AttemptStartResponse.Option(option.id(), option.text()))
								.toList(),
						question.marks()))
				.toList();

		return new AttemptStartResponse(
				attempt.getId(),
				quiz.getId(),
				quiz.getTitle(),
				quiz.getDescription(),
				quiz.getTimeLimitMinutes(),
				attempt.getStartedAt(),
				attempt.getDeadline(),
				attempt.getAttemptNo(),
				quiz.getMaxAttempts(),
				resumed,
				attempt.getMaxScore(),
				questions);
	}

	// --- submitting -------------------------------------------------------------------------------

	/**
	 * Marks the paper.
	 *
	 * <p>Grading is exact-match and all-or-nothing: a question earns its full marks when the set of
	 * options chosen equals the set keyed as correct, and zero otherwise. A multi-select with two of
	 * three right answers therefore scores nothing — partial credit is a policy no school has set here,
	 * and inventing one would make a quiz's marks mean something different per deployment.
	 *
	 * <p>Questions left out of the submission are skipped and score zero. An unknown question id, a
	 * repeated one, an option that is not on its question, or two options on a single-answer question
	 * are all rejected: each means the client is marking a different paper from the one on the server.
	 */
	public AttemptResultResponse submit(String attemptId, SubmitAttemptRequest request) {
		AuthPrincipal caller = CurrentUser.require();
		QuizAttempt attempt = attempts.findById(attemptId)
				.orElseThrow(() -> NotFoundException.of("Quiz attempt", attemptId));
		if (!caller.uniqueId().equals(attempt.getStudentUniqueId())) {
			throw new ForbiddenException("You may only submit your own attempt");
		}
		if (attempt.isSubmitted()) {
			throw new ConflictException("This attempt was already submitted at " + attempt.getSubmittedAt());
		}

		Instant now = Instant.now(clock);
		if (now.isAfter(attempt.getDeadline().plus(SUBMIT_GRACE))) {
			throw new BusinessRuleException("The time limit for this attempt ran out at " + attempt.getDeadline());
		}

		Quiz quiz = quizzes.findById(attempt.getQuizId())
				.orElseThrow(() -> NotFoundException.of("Quiz", attempt.getQuizId()));
		List<AttemptAnswer> graded = grade(quiz, request.answers());
		int score = graded.stream().mapToInt(AttemptAnswer::marksAwarded).sum();

		QuizAttempt submitted = attempts.save(attempt.toBuilder()
				.answers(graded)
				.score(score)
				.submittedAt(now)
				.build());

		return new AttemptResultResponse(
				submitted.getId(),
				quiz.getId(),
				quiz.getTitle(),
				submitted.getScore(),
				submitted.getMaxScore(),
				submitted.getSubmittedAt(),
				quiz.isShowAnswersAfterSubmit() ? review(quiz, submitted) : null);
	}

	private static List<AttemptAnswer> grade(Quiz quiz, List<SubmitAttemptRequest.Answer> submitted) {
		Map<String, QuizQuestion> byId = questionsById(quiz);
		Set<String> answered = new HashSet<>();
		List<AttemptAnswer> graded = new ArrayList<>(submitted.size());

		for (int i = 0; i < submitted.size(); i++) {
			SubmitAttemptRequest.Answer answer = submitted.get(i);
			String path = "answers[" + i + "]";
			QuizQuestion question = byId.get(answer.questionId());
			if (question == null) {
				throw new ValidationException("That question is not on this quiz",
						List.of(new FieldViolation(path + ".questionId", "no question " + answer.questionId())));
			}
			if (!answered.add(answer.questionId())) {
				throw new ValidationException("One question was answered twice",
						List.of(new FieldViolation(path + ".questionId", "repeated question "
								+ answer.questionId())));
			}

			Set<String> optionIds = new HashSet<>(question.options().stream().map(QuestionOption::id).toList());
			List<String> selected = answer.selectedOptionIds().stream().map(String::trim).distinct().toList();
			for (String option : selected) {
				if (!optionIds.contains(option)) {
					throw new ValidationException("That option is not on this question",
							List.of(new FieldViolation(path + ".selectedOptionIds", "no option " + option)));
				}
			}
			if (question.type() != QuestionType.MCQ_MULTI && selected.size() > 1) {
				throw new ValidationException("Only one option may be chosen on this question",
						List.of(new FieldViolation(path + ".selectedOptionIds",
								selected.size() + " chosen on a " + question.type() + " question")));
			}

			// A blank answer is never correct, even against a question whose key somehow went missing:
			// two empty sets are equal, and awarding marks for that would be a gift.
			boolean correct = !selected.isEmpty()
					&& new HashSet<>(selected).equals(new HashSet<>(question.correctOptionIds()));
			graded.add(new AttemptAnswer(question.id(), selected, correct, correct ? question.marks() : 0));
		}
		return List.copyOf(graded);
	}

	/** The per-question review, in this attempt's own question order. Only built when the quiz allows it. */
	private static List<AttemptResultResponse.Question> review(Quiz quiz, QuizAttempt attempt) {
		Map<String, QuizQuestion> byId = questionsById(quiz);
		Map<String, AttemptAnswer> answers = new LinkedHashMap<>();
		for (AttemptAnswer answer : attempt.getAnswers()) {
			answers.put(answer.questionId(), answer);
		}

		List<AttemptResultResponse.Question> review = new ArrayList<>();
		for (String questionId : attempt.getQuestionOrder()) {
			QuizQuestion question = byId.get(questionId);
			if (question == null) {
				continue;
			}
			AttemptAnswer answer = answers.get(questionId);
			review.add(new AttemptResultResponse.Question(
					question.id(),
					question.text(),
					question.options().stream()
							.map(option -> new AttemptResultResponse.Option(option.id(), option.text()))
							.toList(),
					answer == null ? List.of() : answer.selectedOptionIds(),
					question.correctOptionIds(),
					answer != null && answer.correct(),
					question.marks(),
					answer == null ? 0 : answer.marksAwarded(),
					question.explanation()));
		}
		return review;
	}

	// --- history ----------------------------------------------------------------------------------

	/** This student's attempts, newest first, with what each scored. */
	public List<MyAttemptResponse> mine() {
		AuthPrincipal caller = CurrentUser.require();
		return attempts.findByStudentUniqueIdOrderByStartedAtDesc(caller.uniqueId()).stream()
				.map(MyAttemptResponse::of)
				.toList();
	}

	// --- shared -----------------------------------------------------------------------------------

	private static Map<String, QuizQuestion> questionsById(Quiz quiz) {
		Map<String, QuizQuestion> byId = new LinkedHashMap<>();
		if (quiz.getQuestions() != null) {
			for (QuizQuestion question : quiz.getQuestions()) {
				byId.put(question.id(), question);
			}
		}
		return byId;
	}

	/**
	 * The caller as a student. {@code QUIZ_ATTEMPT} is a student's permission, so a login that holds it
	 * without a student record is a broken account rather than a bad request — but it is still refused
	 * here rather than allowed to produce a nameless attempt.
	 */
	private StudentRef requireStudent() {
		AuthPrincipal caller = CurrentUser.require();
		return students.findRef(caller.uniqueId())
				.orElseThrow(() -> new ForbiddenException("Only a student can sit a quiz"));
	}

	/**
	 * The quiz, if this student may sit it at all.
	 *
	 * <p>A draft is a 404: a student should not be able to learn that a quiz is being written, let alone
	 * which class it is for. A closed one is a 422 with the reason, because they could legitimately have
	 * had it on screen a minute ago.
	 */
	private Quiz requireSittable(String quizId, StudentRef student) {
		Quiz quiz = quizzes.findById(quizId).orElseThrow(() -> NotFoundException.of("Quiz", quizId));
		if (quiz.getStatus() == QuizStatus.DRAFT) {
			throw NotFoundException.of("Quiz", quizId);
		}
		if (quiz.getStatus() == QuizStatus.CLOSED) {
			throw new BusinessRuleException("This quiz has been closed");
		}
		if (!quiz.getClassId().equals(student.classId()) || !quiz.coversSection(student.section())) {
			throw new ForbiddenException("This quiz was not set for your class");
		}
		return quiz;
	}
}

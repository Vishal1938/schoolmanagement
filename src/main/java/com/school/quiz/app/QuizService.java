package com.school.quiz.app;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.school.academics.app.SchoolClassService;
import com.school.academics.app.SubjectService;
import com.school.academics.domain.SchoolClass;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ForbiddenException;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ValidationException;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.CurrentUser;
import com.school.common.security.Permission;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import com.school.quiz.api.QuizRequest;
import com.school.quiz.api.QuizResultsResponse;
import com.school.quiz.api.QuizSummaryResponse;
import com.school.quiz.domain.AttemptAnswer;
import com.school.quiz.domain.QuestionOption;
import com.school.quiz.domain.QuestionType;
import com.school.quiz.domain.Quiz;
import com.school.quiz.domain.QuizAttempt;
import com.school.quiz.domain.QuizQuestion;
import com.school.quiz.domain.QuizStatus;
import com.school.quiz.infra.QuizAttemptRepository;
import com.school.quiz.infra.QuizRepository;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Quizzes as their authors see them: writing, publishing, closing and reading the results.
 *
 * <p><strong>Who may touch which quiz is decided here, not in an annotation.</strong> An admin and a
 * teacher both hold {@code QUIZ_MANAGE} — that is what gets them to these endpoints at all — and an
 * admin additionally holds {@code QUIZ_MANAGE_ALL}. A teacher without it is held to the quizzes they
 * wrote: their list is filtered to their own, and every read or write of somebody else's is a 403.
 * Asking it as a permission rather than as {@code role == ADMIN} is what CLAUDE.md rule 1 requires.
 *
 * <p><strong>A quiz is only editable while it is a draft.</strong> Publication freezes the question
 * set, because an attempt stores answers by question id and a score against a total: editing a
 * published quiz would silently re-mark papers that have already been handed back. The split runs
 * through the validation too — writing checks that the questions are <em>well-formed</em>, publishing
 * checks that they are <em>complete</em>.
 */
@Service
public class QuizService {

	private static final String AUDIT_ENTITY = "Quiz";

	/** The author's list: newest first. A quiz is worked on for days and sat in an hour. */
	private static final Sort LIST_SORT = Sort.by(Sort.Order.desc("createdAt"));

	private final QuizRepository quizzes;
	private final QuizAttemptRepository attempts;
	private final MongoOperations mongo;
	private final SchoolClassService classes;
	private final SubjectService subjects;
	private final StudentService students;
	private final AuditService audit;
	private final Clock clock;

	public QuizService(QuizRepository quizzes, QuizAttemptRepository attempts, MongoOperations mongo,
			SchoolClassService classes, SubjectService subjects, StudentService students, AuditService audit,
			Clock clock) {
		this.quizzes = quizzes;
		this.attempts = attempts;
		this.mongo = mongo;
		this.classes = classes;
		this.subjects = subjects;
		this.students = students;
		this.audit = audit;
		this.clock = clock;
	}

	// --- reading ----------------------------------------------------------------------------------

	/**
	 * The quizzes this caller may manage, optionally narrowed to a class or a status. An admin sees
	 * every quiz; a teacher sees their own, which is a clause in the query rather than a filter over
	 * the results, so somebody else's quiz is never loaded in the first place.
	 */
	public List<QuizSummaryResponse> list(String classId, QuizStatus status) {
		AuthPrincipal caller = CurrentUser.require();
		List<Criteria> clauses = new ArrayList<>();
		if (!mayManageAnything(caller)) {
			clauses.add(Criteria.where("createdBy").is(caller.uniqueId()));
		}
		if (classId != null && !classId.isBlank()) {
			clauses.add(Criteria.where("classId").is(classId.trim()));
		}
		if (status != null) {
			clauses.add(Criteria.where("status").is(status));
		}

		Query query = clauses.isEmpty() ? new Query() : new Query(new Criteria().andOperator(clauses));
		List<Quiz> found = mongo.find(query.with(LIST_SORT), Quiz.class);
		Map<String, Long> counts = attemptCounts(found.stream().map(Quiz::getId).toList());
		return found.stream()
				.map(quiz -> QuizSummaryResponse.of(quiz, counts.getOrDefault(quiz.getId(), 0L)))
				.toList();
	}

	/**
	 * How many attempts each of these quizzes has, in one aggregation rather than a count per row.
	 * Quizzes nobody has sat are simply absent from the result.
	 */
	private Map<String, Long> attemptCounts(List<String> quizIds) {
		if (quizIds.isEmpty()) {
			return Map.of();
		}
		Aggregation aggregation = Aggregation.newAggregation(
				Aggregation.match(Criteria.where("quizId").in(quizIds)),
				Aggregation.group("quizId").count().as("attempts"));
		Map<String, Long> counts = new HashMap<>();
		for (Document row : mongo.aggregate(aggregation, QuizAttempt.COLLECTION, Document.class)) {
			counts.put(row.getString("_id"), ((Number) row.get("attempts")).longValue());
		}
		return counts;
	}

	/** One quiz in full, <strong>answer key included</strong>. The author's view; never a student's. */
	public Quiz get(String id) {
		Quiz quiz = require(id);
		requireMayManage(quiz, CurrentUser.require());
		return quiz;
	}

	// --- writing ----------------------------------------------------------------------------------

	public Quiz create(QuizRequest request) {
		AuthPrincipal caller = CurrentUser.require();
		Content content = validate(request);
		Instant now = Instant.now(clock);

		Quiz saved = quizzes.insert(Quiz.builder()
				.title(content.title())
				.description(content.description())
				.subjectId(content.subjectId())
				.classId(content.classId())
				.sections(content.sections())
				.timeLimitMinutes(request.timeLimitMinutes())
				.startAt(request.startAt())
				.endAt(request.endAt())
				.maxAttempts(content.maxAttempts())
				.shuffleQuestions(request.shuffleQuestions())
				.showAnswersAfterSubmit(request.showAnswersAfterSubmit())
				.status(QuizStatus.DRAFT)
				.questions(content.questions())
				.createdBy(caller.uniqueId())
				.createdByName(caller.name())
				.createdAt(now)
				.updatedAt(now)
				.build());

		audit.record(AuditAction.QUIZ_CREATED, AUDIT_ENTITY, saved.getId(), null, saved);
		return saved;
	}

	/**
	 * Replaces a draft. The author is never reassigned — the results sheet names whoever set the quiz,
	 * even when an admin corrected it afterwards.
	 */
	public Quiz update(String id, QuizRequest request) {
		AuthPrincipal caller = CurrentUser.require();
		Quiz before = require(id);
		requireMayManage(before, caller);
		requireStatus(before, QuizStatus.DRAFT, "edited");
		Content content = validate(request);

		Quiz saved = quizzes.save(before.toBuilder()
				.title(content.title())
				.description(content.description())
				.subjectId(content.subjectId())
				.classId(content.classId())
				.sections(content.sections())
				.timeLimitMinutes(request.timeLimitMinutes())
				.startAt(request.startAt())
				.endAt(request.endAt())
				.maxAttempts(content.maxAttempts())
				.shuffleQuestions(request.shuffleQuestions())
				.showAnswersAfterSubmit(request.showAnswersAfterSubmit())
				.questions(content.questions())
				.updatedAt(Instant.now(clock))
				.build());

		audit.record(AuditAction.QUIZ_UPDATED, AUDIT_ENTITY, id, before, saved);
		return saved;
	}

	/**
	 * Makes a quiz sittable, which is also the point at which it stops being editable.
	 *
	 * <p>Everything a draft was allowed to be missing is required here, and all of it is reported at
	 * once: a teacher fixing a half-written quiz should see every gap in one response rather than
	 * discover them one publish at a time.
	 */
	public Quiz publish(String id) {
		AuthPrincipal caller = CurrentUser.require();
		Quiz quiz = require(id);
		requireMayManage(quiz, caller);
		requireStatus(quiz, QuizStatus.DRAFT, "published");
		requireReadyToPublish(quiz);

		Instant now = Instant.now(clock);
		Quiz saved = quizzes.save(quiz.toBuilder()
				.status(QuizStatus.PUBLISHED)
				.publishedAt(now)
				.updatedAt(now)
				.build());

		audit.record(AuditAction.QUIZ_PUBLISHED, AUDIT_ENTITY, id, quiz, saved);
		return saved;
	}

	/**
	 * Stops a published quiz, whatever its window says. Attempts already in flight are left alone and
	 * run to their own deadline — closing a quiz should not void a paper somebody is halfway through.
	 */
	public Quiz close(String id) {
		AuthPrincipal caller = CurrentUser.require();
		Quiz quiz = require(id);
		requireMayManage(quiz, caller);
		requireStatus(quiz, QuizStatus.PUBLISHED, "closed");

		Instant now = Instant.now(clock);
		Quiz saved = quizzes.save(quiz.toBuilder()
				.status(QuizStatus.CLOSED)
				.closedAt(now)
				.updatedAt(now)
				.build());

		audit.record(AuditAction.QUIZ_CLOSED, AUDIT_ENTITY, id, quiz, saved);
		return saved;
	}

	/**
	 * Deletes a draft. Only a draft: once a quiz has been published it may have been sat, and deleting
	 * it would leave attempts pointing at questions nobody can read. Closing is the way to retire one.
	 */
	public void delete(String id) {
		AuthPrincipal caller = CurrentUser.require();
		Quiz quiz = require(id);
		requireMayManage(quiz, caller);
		requireStatus(quiz, QuizStatus.DRAFT, "deleted");
		quizzes.delete(quiz);
		audit.record(AuditAction.QUIZ_DELETED, AUDIT_ENTITY, id, quiz, null);
	}

	// --- results ----------------------------------------------------------------------------------

	/**
	 * The results sheet, built from the class roll rather than from the attempts — which is the only
	 * way {@code notAttempted} can exist.
	 *
	 * <p>A student who sat the quiz but is no longer on the roll (they have left, or moved section) is
	 * still listed, at the end and without a roll number: their attempt happened, and dropping it would
	 * make the per-question figures disagree with the table above them.
	 *
	 * <p>{@code averageScore} is the mean of the <em>best</em> score per student who submitted, not of
	 * every attempt: in a quiz allowing three tries, the mean over attempts is dragged down by the
	 * practice runs and is not what anybody means by a class average.
	 */
	public QuizResultsResponse results(String id) {
		Quiz quiz = require(id);
		requireMayManage(quiz, CurrentUser.require());

		List<StudentRef> roll = roll(quiz);
		List<QuizAttempt> all = attempts.findByQuizId(id);
		Map<String, List<QuizAttempt>> byStudent = new LinkedHashMap<>();
		for (QuizAttempt attempt : all) {
			byStudent.computeIfAbsent(attempt.getStudentUniqueId(), key -> new ArrayList<>()).add(attempt);
		}

		List<QuizResultsResponse.StudentResult> results = new ArrayList<>();
		List<QuizResultsResponse.StudentRow> notAttempted = new ArrayList<>();
		for (StudentRef student : roll) {
			List<QuizAttempt> mine = byStudent.remove(student.uniqueId());
			if (mine == null || mine.isEmpty()) {
				notAttempted.add(new QuizResultsResponse.StudentRow(
						student.uniqueId(), student.name(), student.section(), student.rollNo()));
			}
			else {
				results.add(toResult(student.uniqueId(), student.name(), student.section(), student.rollNo(), mine));
			}
		}
		// Whatever is left attempted the quiz without being on today's roll. Listed last, roll number 0.
		byStudent.forEach((uniqueId, mine) -> results.add(toResult(uniqueId, mine.get(0).getStudentName(),
				mine.get(0).getSection(), 0, mine)));

		results.sort(Comparator
				.comparing((QuizResultsResponse.StudentResult row) -> row.bestScore() == null ? -1 : row.bestScore())
				.reversed()
				.thenComparing(QuizResultsResponse.StudentResult::name));

		List<QuizAttempt> submitted = all.stream().filter(QuizAttempt::isSubmitted).toList();
		List<Integer> bestScores = results.stream().map(QuizResultsResponse.StudentResult::bestScore)
				.filter(score -> score != null)
				.toList();

		return new QuizResultsResponse(
				quiz.getId(),
				quiz.getTitle(),
				quiz.getClassId(),
				quiz.getSections() == null ? List.of() : quiz.getSections(),
				quiz.totalMarks(),
				roll.size(),
				results.size(),
				submitted.size(),
				bestScores.isEmpty() ? null
						: Math.round(bestScores.stream().mapToInt(Integer::intValue).average().orElse(0) * 10.0) / 10.0,
				results,
				notAttempted,
				accuracy(quiz, submitted));
	}

	private static QuizResultsResponse.StudentResult toResult(String uniqueId, String name, String section,
			int rollNo, List<QuizAttempt> mine) {
		Optional<QuizAttempt> best = mine.stream()
				.filter(QuizAttempt::isSubmitted)
				.max(Comparator.comparingInt(QuizAttempt::getScore));
		return new QuizResultsResponse.StudentResult(
				uniqueId,
				name,
				section,
				rollNo,
				mine.size(),
				best.map(QuizAttempt::getScore).orElse(null),
				mine.get(0).getMaxScore(),
				best.map(QuizAttempt::getSubmittedAt).orElse(null));
	}

	/**
	 * Per-question accuracy over the submitted attempts. A blank answer counts as wrong — see
	 * {@link QuizResultsResponse.QuestionAccuracy} for why the denominator is the attempt count and not
	 * the number of students who answered.
	 */
	private static List<QuizResultsResponse.QuestionAccuracy> accuracy(Quiz quiz, List<QuizAttempt> submitted) {
		if (quiz.getQuestions() == null) {
			return List.of();
		}
		List<QuizResultsResponse.QuestionAccuracy> rows = new ArrayList<>(quiz.getQuestions().size());
		for (QuizQuestion question : quiz.getQuestions()) {
			long correct = 0;
			long answered = 0;
			for (QuizAttempt attempt : submitted) {
				AttemptAnswer answer = answerFor(attempt, question.id());
				if (answer == null || answer.selectedOptionIds() == null || answer.selectedOptionIds().isEmpty()) {
					continue;
				}
				answered++;
				if (answer.correct()) {
					correct++;
				}
			}
			rows.add(new QuizResultsResponse.QuestionAccuracy(
					question.id(),
					question.text(),
					question.marks(),
					correct,
					answered,
					submitted.size(),
					submitted.isEmpty() ? null : Math.round(1_000.0 * correct / submitted.size()) / 10.0));
		}
		return rows;
	}

	private static AttemptAnswer answerFor(QuizAttempt attempt, String questionId) {
		if (attempt.getAnswers() == null) {
			return null;
		}
		return attempt.getAnswers().stream()
				.filter(answer -> questionId.equals(answer.questionId()))
				.findFirst()
				.orElse(null);
	}

	/**
	 * The students this quiz was set for, in section-then-roll order: the sections named on the quiz,
	 * or every section of the class when it names none.
	 */
	private List<StudentRef> roll(Quiz quiz) {
		SchoolClass schoolClass = classes.findById(quiz.getClassId()).orElse(null);
		List<String> sections = quiz.getSections() == null || quiz.getSections().isEmpty()
				? (schoolClass == null || schoolClass.getSections() == null ? List.of() : schoolClass.getSections())
				: quiz.getSections();
		return sections.stream()
				.flatMap(section -> students.activeInSection(quiz.getClassId(), section).stream())
				.toList();
	}

	// --- shared with the student side -------------------------------------------------------------

	Quiz require(String id) {
		return quizzes.findById(id).orElseThrow(() -> NotFoundException.of("Quiz", id));
	}

	// --- authorization ----------------------------------------------------------------------------

	/** An admin may manage any quiz; a teacher only the ones they wrote. */
	private static void requireMayManage(Quiz quiz, AuthPrincipal caller) {
		if (mayManageAnything(caller)) {
			return;
		}
		if (!caller.uniqueId().equals(quiz.getCreatedBy())) {
			throw new ForbiddenException("You may only work with quizzes you set yourself");
		}
	}

	private static boolean mayManageAnything(AuthPrincipal caller) {
		return caller.permissions().contains(Permission.QUIZ_MANAGE_ALL);
	}

	// --- validation -------------------------------------------------------------------------------

	/** The normalized, checked content of a write, shared by create and update. */
	private record Content(String title, String description, String subjectId, String classId, List<String> sections,
			int maxAttempts, List<QuizQuestion> questions) {
	}

	private Content validate(QuizRequest request) {
		String classId = request.classId().trim();
		SchoolClass schoolClass = classes.findById(classId)
				.orElseThrow(() -> new ValidationException("That class does not exist",
						List.of(new FieldViolation("classId", "no class with id " + classId))));

		String subjectId = request.subjectId().trim();
		if (!subjects.findMissingIds(List.of(subjectId)).isEmpty()) {
			throw new ValidationException("That subject does not exist",
					List.of(new FieldViolation("subjectId", "no subject with id " + subjectId)));
		}

		if (request.startAt() != null && request.endAt() != null && !request.endAt().isAfter(request.startAt())) {
			throw new ValidationException("The quiz would close before it opens",
					List.of(new FieldViolation("endAt", "must be after startAt")));
		}

		return new Content(
				request.title().trim(),
				blankToNull(request.description()),
				subjectId,
				classId,
				sections(request.sections(), schoolClass),
				request.maxAttempts() == null ? 1 : request.maxAttempts(),
				questions(request.questions()));
	}

	/** Upper-cased and de-duplicated, and every one has to be a section the class actually has. */
	private static List<String> sections(List<String> requested, SchoolClass schoolClass) {
		if (requested == null || requested.isEmpty()) {
			return List.of();
		}
		List<String> known = schoolClass.getSections() == null ? List.of() : schoolClass.getSections();
		Set<String> normalized = new LinkedHashSet<>();
		for (String section : requested) {
			String value = section.trim().toUpperCase(Locale.ROOT);
			if (!known.contains(value)) {
				throw new ValidationException("That class has no such section",
						List.of(new FieldViolation("sections",
								schoolClass.getName() + " has no section " + value)));
			}
			normalized.add(value);
		}
		return List.copyOf(normalized);
	}

	/**
	 * Checks that the questions are <strong>well-formed</strong>, and fills in the ids a new question
	 * or option does not have yet.
	 *
	 * <p>Well-formed is not the same as complete: an answer key may be empty here, because that is what
	 * a half-written draft looks like. What is checked is that nothing is self-contradictory — no two
	 * questions or options share an id, every keyed answer is one of that question's own options, and
	 * the type's arity holds, so a single-answer question cannot be stored with two right answers.
	 */
	private static List<QuizQuestion> questions(List<QuizRequest.Question> requested) {
		if (requested == null || requested.isEmpty()) {
			return List.of();
		}
		Set<String> questionIds = new HashSet<>();
		List<QuizQuestion> result = new ArrayList<>(requested.size());
		for (int i = 0; i < requested.size(); i++) {
			QuizRequest.Question question = requested.get(i);
			String path = "questions[" + i + "]";

			String id = idOrGenerated(question.id());
			if (!questionIds.add(id)) {
				throw new ValidationException("Two questions share an id",
						List.of(new FieldViolation(path + ".id", "duplicate question id " + id)));
			}

			List<QuestionOption> options = options(question.options(), path);
			Set<String> optionIds = options.stream().map(QuestionOption::id).collect(Collectors.toSet());
			List<String> correct = question.correctOptionIds() == null
					? List.of()
					: question.correctOptionIds().stream().map(String::trim).distinct().toList();
			for (String answer : correct) {
				if (!optionIds.contains(answer)) {
					throw new ValidationException("A correct answer names an option this question does not have",
							List.of(new FieldViolation(path + ".correctOptionIds", "no option " + answer)));
				}
			}
			requireArity(question.type(), options.size(), correct.size(), path);

			result.add(new QuizQuestion(id, question.type(), question.text().trim(), options, correct,
					question.marks() == null ? 1 : question.marks(), blankToNull(question.explanation())));
		}
		return result;
	}

	private static List<QuestionOption> options(List<QuizRequest.Option> requested, String path) {
		Set<String> ids = new HashSet<>();
		List<QuestionOption> options = new ArrayList<>(requested.size());
		for (int i = 0; i < requested.size(); i++) {
			QuizRequest.Option option = requested.get(i);
			String id = idOrGenerated(option.id());
			if (!ids.add(id)) {
				throw new ValidationException("Two options on one question share an id",
						List.of(new FieldViolation(path + ".options[" + i + "].id", "duplicate option id " + id)));
			}
			options.add(new QuestionOption(id, option.text().trim()));
		}
		return List.copyOf(options);
	}

	/**
	 * The structural rules the type stands for. Option counts below two are already refused by Bean
	 * Validation; what is left is the upper bound on a true/false and the "at most one right answer" of
	 * the single-choice types.
	 */
	private static void requireArity(QuestionType type, int optionCount, int correctCount, String path) {
		if (type == QuestionType.TRUE_FALSE && optionCount != 2) {
			throw new ValidationException("A true/false question has exactly two options",
					List.of(new FieldViolation(path + ".options", "has " + optionCount + ", needs 2")));
		}
		if (type != QuestionType.MCQ_MULTI && correctCount > 1) {
			throw new ValidationException("Only an MCQ_MULTI question may have more than one correct answer",
					List.of(new FieldViolation(path + ".correctOptionIds",
							"has " + correctCount + " for a " + type + " question")));
		}
	}

	/** Everything a draft was allowed to be missing. Reported in one go. */
	private static void requireReadyToPublish(Quiz quiz) {
		List<FieldViolation> problems = new ArrayList<>();
		if (quiz.questionCount() == 0) {
			problems.add(new FieldViolation("questions", "a quiz needs at least one question"));
		}
		else {
			List<QuizQuestion> questions = quiz.getQuestions();
			for (int i = 0; i < questions.size(); i++) {
				List<String> correct = questions.get(i).correctOptionIds();
				if (correct == null || correct.isEmpty()) {
					problems.add(new FieldViolation("questions[" + i + "].correctOptionIds",
							"no correct answer is marked"));
				}
			}
		}
		if (quiz.getStartAt() == null) {
			problems.add(new FieldViolation("startAt", "is required to publish"));
		}
		if (quiz.getEndAt() == null) {
			problems.add(new FieldViolation("endAt", "is required to publish"));
		}
		else if (quiz.getStartAt() != null && !quiz.getEndAt().isAfter(quiz.getStartAt())) {
			problems.add(new FieldViolation("endAt", "must be after startAt"));
		}
		if (!problems.isEmpty()) {
			throw new ValidationException("This quiz is not ready to be published", problems);
		}
	}

	private static void requireStatus(Quiz quiz, QuizStatus required, String verb) {
		if (quiz.getStatus() != required) {
			throw new ConflictException("A quiz can only be " + verb + " while it is " + required
					+ "; this one is " + quiz.getStatus());
		}
	}

	private static String idOrGenerated(String id) {
		return id == null || id.isBlank()
				? UUID.randomUUID().toString().replace("-", "").substring(0, 12)
				: id.trim();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

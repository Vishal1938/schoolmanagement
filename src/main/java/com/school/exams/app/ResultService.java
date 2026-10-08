package com.school.exams.app;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.school.academics.app.AcademicContext;
import com.school.academics.app.SchoolClassService;
import com.school.academics.app.SubjectService;
import com.school.academics.domain.SchoolClass;
import com.school.academics.domain.Subject;
import com.school.common.exceptions.BusinessRuleException;
import com.school.common.exceptions.ForbiddenException;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.CurrentUser;
import com.school.common.security.Permission;
import com.school.exams.api.ClassResultSheetResponse;
import com.school.exams.api.ExamResult;
import com.school.exams.api.ResultOutcome;
import com.school.exams.api.StudentResultsResponse;
import com.school.exams.domain.Exam;
import com.school.exams.domain.ExamStatus;
import com.school.exams.domain.ExamSubject;
import com.school.exams.domain.Mark;
import com.school.exams.infra.MarkRepository;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import com.school.schoolconfig.app.SchoolConfigService;
import com.school.schoolconfig.domain.GradeBand;
import org.springframework.stereotype.Service;

/**
 * Turning marks into results: totals, percentages, grades, pass or fail, and rank.
 *
 * <p>Two decisions are worth stating because they are where report cards usually go wrong.
 *
 * <p><strong>{@code maxTotal} counts every paper in the schedule</strong>, not only the marked
 * ones. If a subject was never marked, the student's percentage drops rather than the exam quietly
 * shrinking to the subjects that happen to have marks — which would let an unmarked paper flatter
 * somebody.
 *
 * <p><strong>Grades come from the floored percentage.</strong> 89.6% is graded as 89, because a
 * band starting at 90 means ninety. The {@code percentage} shown is still the unrounded value to
 * two decimals, so a report card can read "89.60% — B1" without either number being wrong.
 */
@Service
public class ResultService {

	private final MarkRepository marks;
	private final ExamService exams;
	private final StudentService students;
	private final SubjectService subjects;
	private final SchoolClassService classes;
	private final SchoolConfigService schoolConfig;
	private final AcademicContext academicContext;

	public ResultService(MarkRepository marks, ExamService exams, StudentService students, SubjectService subjects,
			SchoolClassService classes, SchoolConfigService schoolConfig, AcademicContext academicContext) {
		this.marks = marks;
		this.exams = exams;
		this.students = students;
		this.subjects = subjects;
		this.classes = classes;
		this.schoolConfig = schoolConfig;
		this.academicContext = academicContext;
	}

	// --- one student ------------------------------------------------------------------------------

	/**
	 * Every result a student has in a session.
	 *
	 * <p>A student may read only their own, and then only PUBLISHED exams. An admin or teacher sees
	 * MARKS_ENTRY exams too, so a sheet can be checked before it is released.
	 */
	public StudentResultsResponse forStudent(String uniqueId, String sessionId) {
		StudentRef student = students.requireRef(uniqueId);
		boolean staffView = requireMayRead(student);

		String session = sessionId == null || sessionId.isBlank() ? academicContext.currentSessionId() : sessionId;
		Map<String, String> subjectNames = subjectNames();

		List<ExamResult> results = exams.list(session, student.classId()).stream()
				.filter(exam -> staffView ? exam.getStatus() != ExamStatus.DRAFT
						: exam.getStatus() == ExamStatus.PUBLISHED)
				.map(exam -> resultFor(exam, student, subjectNames))
				.toList();

		SchoolClass schoolClass = classes.findById(student.classId()).orElse(null);
		return new StudentResultsResponse(student.uniqueId(), student.name(),
				schoolClass == null ? null : schoolClass.getName(), student.section(), session, results);
	}

	/**
	 * One student's result in one exam, with the student and class the report card (B9) prints
	 * alongside it.
	 *
	 * <p>PUBLISHED only, for an admin as much as for the student. A report card is the school's
	 * statement of a result, and an exam still in marks entry has no result to state — the class
	 * result sheet is where an unreleased mark sheet is checked.
	 */
	public StudentExamResult forStudentExam(String uniqueId, String examId) {
		StudentRef student = students.requireRef(uniqueId);
		requireMayRead(student);

		Exam exam = exams.get(examId);
		if (exam.getStatus() != ExamStatus.PUBLISHED) {
			throw new BusinessRuleException("The results of " + exam.getName() + " have not been published yet");
		}
		if (student.classId() == null || student.section() == null) {
			throw new BusinessRuleException(student.name() + " has no current enrollment, so there is no class to "
					+ "report on");
		}
		SchoolClass schoolClass = exams.requireClassSitsExam(exam, student.classId(), student.section());

		return new StudentExamResult(student, schoolClass, exam, resultFor(exam, student, subjectNames()));
	}

	/**
	 * One student's result in one exam, with who they are and which class they sat it in.
	 *
	 * @param schoolClass their class, which is known to sit this exam and to have their section
	 */
	public record StudentExamResult(StudentRef student, SchoolClass schoolClass, Exam exam, ExamResult result) {
	}

	// --- what leaves the module (B18) -------------------------------------------------------------

	/**
	 * Which session an exam belongs to.
	 *
	 * <p>For a caller that holds an exam id and nothing else — the AI report remark is asked for by
	 * exam, and then has to look up the same student's earlier exams in that exam's year rather than
	 * in whatever year happens to be current.
	 */
	public String sessionOfExam(String examId) {
		return exams.get(examId).getSessionId();
	}

	/**
	 * One student's results in the PUBLISHED exams of a session, oldest exam first.
	 *
	 * <p>PUBLISHED only, for an admin as much as for the student, and for the same reason
	 * {@link #forStudentExam} is: an exam still in marks entry has no result to state. Oldest first
	 * is what makes "the previous exam" meaningful — see {@link StudentExamScore#lastPaperOn()}.
	 *
	 * @param sessionId null or blank for the current session
	 */
	public List<StudentExamScore> publishedScores(String uniqueId, String sessionId) {
		StudentRef student = students.requireRef(uniqueId);
		requireMayRead(student);

		Map<String, String> subjectNames = subjectNames();
		return publishedExams(sessionId, student.classId()).stream()
				.map(exam -> scoreOf(exam, marksOf(exam.getId(), student.uniqueId()), subjectNames))
				.toList();
	}

	/**
	 * The same, for every ACTIVE student of one class at once, keyed by unique id.
	 *
	 * <p>One marks read per exam rather than one per student per exam, which is the difference
	 * between a school-wide scan being usable and not: B18's insights endpoint walks every class.
	 * A student with no marks at all still gets a list — of zeroes — because an unmarked paper is
	 * part of what the flags are looking for.
	 *
	 * @param sessionId null or blank for the current session
	 */
	public Map<String, List<StudentExamScore>> publishedScoresByClass(String sessionId, String classId) {
		requireStaffRead();

		String session = sessionId == null || sessionId.isBlank() ? academicContext.currentSessionId() : sessionId;
		List<StudentRef> roll = students.activeInClass(session, classId);
		if (roll.isEmpty()) {
			return Map.of();
		}

		Map<String, String> subjectNames = subjectNames();
		Map<String, List<StudentExamScore>> byStudent = new LinkedHashMap<>();
		roll.forEach(student -> byStudent.put(student.uniqueId(), new ArrayList<>()));
		for (Exam exam : publishedExams(session, classId)) {
			Map<String, Map<String, Mark>> marksByStudent = marksByStudent(exam.getId(), roll);
			roll.forEach(student -> byStudent.get(student.uniqueId())
					.add(scoreOf(exam, marksByStudent.getOrDefault(student.uniqueId(), Map.of()), subjectNames)));
		}
		return byStudent;
	}

	/** The session's published exams for a class, oldest last paper first. */
	private List<Exam> publishedExams(String sessionId, String classId) {
		return exams.list(sessionId, classId).stream()
				.filter(exam -> exam.getStatus() == ExamStatus.PUBLISHED)
				.sorted(Comparator.comparing(ResultService::lastPaperOn,
						Comparator.nullsFirst(Comparator.naturalOrder())))
				.toList();
	}

	/**
	 * When the last paper of an exam was sat, which is the only chronology an exam has: the schedule
	 * carries the dates and the exam itself only carries a name.
	 */
	private static LocalDate lastPaperOn(Exam exam) {
		return schedule(exam).stream()
				.map(ExamSubject::date)
				.filter(Objects::nonNull)
				.max(Comparator.naturalOrder())
				.orElse(null);
	}

	private StudentExamScore scoreOf(Exam exam, Map<String, Mark> bySubject, Map<String, String> subjectNames) {
		Scored scored = score(exam, bySubject);
		List<StudentExamScore.SubjectScore> subjectScores = new ArrayList<>();
		for (ExamSubject paper : schedule(exam)) {
			Mark mark = bySubject.get(paper.subjectId());
			Integer obtained = mark == null || mark.isAbsent() ? null : mark.getMarksObtained();
			subjectScores.add(new StudentExamScore.SubjectScore(
					subjectNames.get(paper.subjectId()),
					obtained,
					paper.maxMarks(),
					obtained == null ? null : gradeLabel(percentageOf(obtained, paper.maxMarks())),
					obtained != null && obtained >= paper.passMarks(),
					mark != null && mark.isAbsent()));
		}
		GradeBand band = bandFor(floorPercentage(scored.total(), scored.maxTotal()));
		return new StudentExamScore(exam.getId(), exam.getName(), lastPaperOn(exam),
				percentage(scored.total(), scored.maxTotal()), band == null ? null : band.grade(),
				scored.outcome() == ResultOutcome.PASS, subjectScores);
	}

	private Map<String, Mark> marksOf(String examId, String studentUniqueId) {
		return marks.findByExamIdAndStudentUniqueId(examId, studentUniqueId).stream()
				.collect(Collectors.toMap(Mark::getSubjectId, mark -> mark, (first, second) -> first));
	}

	/** Reading a whole class is staff-only; there is no "my own" reading of somebody else's roll. */
	private void requireStaffRead() {
		AuthPrincipal caller = CurrentUser.require();
		if (!caller.permissions().contains(Permission.STUDENT_READ_BASIC)
				&& !caller.permissions().contains(Permission.STUDENT_READ_FULL)) {
			throw new ForbiddenException("You may not read a whole class's results");
		}
	}

	/**
	 * An admin or a teacher may read any student's results; a student may read only their own.
	 *
	 * @return true when the caller is staff, which also decides whether unpublished exams are visible
	 */
	private boolean requireMayRead(StudentRef student) {
		AuthPrincipal caller = CurrentUser.require();
		boolean staffView = caller.permissions().contains(Permission.STUDENT_READ_BASIC)
				|| caller.permissions().contains(Permission.STUDENT_READ_FULL);
		if (!staffView && !student.uniqueId().equals(caller.uniqueId())) {
			throw new ForbiddenException("You may only read your own results");
		}
		return staffView;
	}

	/** One student's result in one exam, including their rank within their class-section. */
	private ExamResult resultFor(Exam exam, StudentRef student, Map<String, String> subjectNames) {
		Map<String, Mark> bySubject = marksOf(exam.getId(), student.uniqueId());
		Scored scored = score(exam, bySubject);

		List<ExamResult.SubjectResult> subjectResults = new ArrayList<>();
		for (ExamSubject paper : schedule(exam)) {
			Mark mark = bySubject.get(paper.subjectId());
			Integer obtained = mark == null || mark.isAbsent() ? null : mark.getMarksObtained();
			boolean absent = mark != null && mark.isAbsent();
			boolean pass = obtained != null && obtained >= paper.passMarks();
			subjectResults.add(new ExamResult.SubjectResult(paper.subjectId(),
					subjectNames.get(paper.subjectId()), obtained, paper.maxMarks(), paper.passMarks(), absent,
					obtained == null ? null : gradeLabel(percentageOf(obtained, paper.maxMarks())),
					pass, mark == null ? null : mark.getRemarks()));
		}

		Ranking ranking = rankWithin(exam, student);
		GradeBand band = bandFor(floorPercentage(scored.total(), scored.maxTotal()));
		return new ExamResult(exam.getId(), exam.getName(), subjectResults, scored.total(), scored.maxTotal(),
				percentage(scored.total(), scored.maxTotal()),
				band == null ? null : band.grade(), band == null ? null : band.remark(),
				ranking.rank(), ranking.outOf(), scored.outcome());
	}

	// --- a class-section --------------------------------------------------------------------------

	/** One class-section's sheet for one exam, best rank first. */
	public ClassResultSheetResponse forClass(String examId, String classId, String section) {
		Exam exam = exams.get(examId);
		SchoolClass schoolClass = exams.requireClassSitsExam(exam, classId, section);
		String normalizedSection = section.trim().toUpperCase(java.util.Locale.ROOT);
		Map<String, String> subjectNames = subjectNames();
		List<ExamSubject> schedule = schedule(exam);

		List<StudentRef> roll = students.activeInSection(classId, normalizedSection);
		Map<String, Map<String, Mark>> byStudent = marksByStudent(exam.getId(), roll);

		record Scoreboard(StudentRef student, Scored scored, Map<String, Mark> marks) {
		}
		List<Scoreboard> scoreboard = roll.stream()
				.map(student -> {
					Map<String, Mark> studentMarks = byStudent.getOrDefault(student.uniqueId(), Map.of());
					return new Scoreboard(student, score(exam, studentMarks), studentMarks);
				})
				.sorted(Comparator.comparingInt((Scoreboard entry) -> entry.scored().total()).reversed())
				.toList();

		Map<String, Integer> ranks = ranksByTotal(scoreboard.stream()
				.collect(LinkedHashMap::new,
						(map, entry) -> map.put(entry.student().uniqueId(), entry.scored().total()),
						LinkedHashMap::putAll));

		List<ClassResultSheetResponse.Row> rows = scoreboard.stream()
				.map(entry -> {
					List<Integer> perSubject = schedule.stream()
							.map(paper -> {
								Mark mark = entry.marks().get(paper.subjectId());
								return mark == null || mark.isAbsent() ? null : mark.getMarksObtained();
							})
							.toList();
					GradeBand band = bandFor(floorPercentage(entry.scored().total(), entry.scored().maxTotal()));
					return new ClassResultSheetResponse.Row(entry.student().uniqueId(), entry.student().name(),
							entry.student().rollNo(), perSubject, entry.scored().total(), entry.scored().maxTotal(),
							percentage(entry.scored().total(), entry.scored().maxTotal()),
							band == null ? null : band.grade(),
							ranks.getOrDefault(entry.student().uniqueId(), 0), entry.scored().outcome());
				})
				.toList();

		List<ClassResultSheetResponse.Column> columns = schedule.stream()
				.map(paper -> new ClassResultSheetResponse.Column(paper.subjectId(),
						subjectNames.get(paper.subjectId()), paper.maxMarks(), paper.passMarks()))
				.toList();

		return new ClassResultSheetResponse(examId, exam.getName(), exam.getStatus(), classId,
				schoolClass.getName(), normalizedSection, columns, rows);
	}

	// --- scoring ----------------------------------------------------------------------------------

	/** The totals and the pass/fail for one student in one exam. */
	private record Scored(int total, int maxTotal, ResultOutcome outcome) {
	}

	private Scored score(Exam exam, Map<String, Mark> bySubject) {
		int total = 0;
		int maxTotal = 0;
		boolean passedEverything = true;
		for (ExamSubject paper : schedule(exam)) {
			// Every paper counts towards maxTotal, marked or not; see the class comment.
			maxTotal += paper.maxMarks();
			Mark mark = bySubject.get(paper.subjectId());
			Integer obtained = mark == null || mark.isAbsent() ? null : mark.getMarksObtained();
			if (obtained != null) {
				total += obtained;
			}
			if (obtained == null || obtained < paper.passMarks()) {
				passedEverything = false;
			}
		}
		return new Scored(total, maxTotal, passedEverything ? ResultOutcome.PASS : ResultOutcome.FAIL);
	}

	private record Ranking(int rank, int outOf) {
	}

	/** This student's rank among the ACTIVE students of their own class-section. */
	private Ranking rankWithin(Exam exam, StudentRef student) {
		if (student.classId() == null || student.section() == null) {
			return new Ranking(0, 0);
		}
		List<StudentRef> roll = students.activeInSection(student.classId(), student.section());
		Map<String, Map<String, Mark>> byStudent = marksByStudent(exam.getId(), roll);
		LinkedHashMap<String, Integer> totals = new LinkedHashMap<>();
		roll.forEach(peer -> totals.put(peer.uniqueId(),
				score(exam, byStudent.getOrDefault(peer.uniqueId(), Map.of())).total()));
		return new Ranking(ranksByTotal(totals).getOrDefault(student.uniqueId(), 0), roll.size());
	}

	/**
	 * Competition ranking by total, highest first: ties share a rank and the next rank skips, so two
	 * students on the same total are both 2nd and the next is 4th.
	 */
	private static Map<String, Integer> ranksByTotal(Map<String, Integer> totalsByStudent) {
		List<Map.Entry<String, Integer>> sorted = totalsByStudent.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.toList();
		Map<String, Integer> ranks = new LinkedHashMap<>();
		int rank = 0;
		int seen = 0;
		Integer previousTotal = null;
		for (Map.Entry<String, Integer> entry : sorted) {
			seen++;
			if (previousTotal == null || !previousTotal.equals(entry.getValue())) {
				rank = seen;
				previousTotal = entry.getValue();
			}
			ranks.put(entry.getKey(), rank);
		}
		return ranks;
	}

	/** Every mark of these students in one read, grouped student then subject. */
	private Map<String, Map<String, Mark>> marksByStudent(String examId, List<StudentRef> roll) {
		if (roll.isEmpty()) {
			return Map.of();
		}
		return marks.findByExamIdAndStudentUniqueIdIn(examId, roll.stream().map(StudentRef::uniqueId).toList())
				.stream()
				.collect(Collectors.groupingBy(Mark::getStudentUniqueId,
						Collectors.toMap(Mark::getSubjectId, mark -> mark, (first, second) -> first)));
	}

	// --- grading ----------------------------------------------------------------------------------

	private String gradeLabel(int flooredPercentage) {
		GradeBand band = bandFor(flooredPercentage);
		return band == null ? null : band.grade();
	}

	/** Null rather than an error when the scheme leaves a gap — a missing grade is not a failed request. */
	private GradeBand bandFor(int flooredPercentage) {
		return schoolConfig.gradingScheme().bandFor(flooredPercentage).orElse(null);
	}

	private static int percentageOf(int obtained, int max) {
		return max <= 0 ? 0 : obtained * 100 / max;
	}

	private static int floorPercentage(int total, int maxTotal) {
		return percentageOf(total, maxTotal);
	}

	private static double percentage(int total, int maxTotal) {
		return maxTotal <= 0 ? 0
				: BigDecimal.valueOf(total * 100.0 / maxTotal).setScale(2, RoundingMode.HALF_UP).doubleValue();
	}

	private static List<ExamSubject> schedule(Exam exam) {
		return exam.getSchedule() == null ? List.of() : exam.getSchedule();
	}

	private Map<String, String> subjectNames() {
		return subjects.list().stream().collect(Collectors.toMap(Subject::getId, Subject::getName));
	}
}

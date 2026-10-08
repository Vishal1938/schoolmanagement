package com.school.ai.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.school.academics.app.SchoolClassService;
import com.school.academics.domain.SchoolClass;
import com.school.ai.api.InsightsRequest;
import com.school.ai.api.StudentInsight;
import com.school.ai.domain.InsightFlag;
import com.school.ai.infra.AiGateway;
import com.school.attendance.app.AttendanceShare;
import com.school.attendance.app.StudentAttendanceService;
import com.school.common.exceptions.AppException;
import com.school.common.fees.FeeStatus;
import com.school.common.fees.StudentFeeStatusProvider;
import com.school.exams.app.StudentExamScore;
import com.school.exams.app.ResultService;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Who in the school needs looking at, and why.
 *
 * <p><strong>The flags are computed here, in code, before the provider is called at all.</strong>
 * Which students are at risk is a judgement the school makes with thresholds it owns, and asking a
 * model to make it would mean a different answer every time, no way to check one, and the records of
 * every child on the roll going out over the wire. So the scan reads attendance, exam results and
 * fee statuses itself, applies the four rules in {@link InsightFlag}, and only then asks the model to
 * put each finding into a sentence — from a first name and the flag names, nothing more.
 *
 * <p><strong>One call, not one per student.</strong> The flagged students go up as a numbered list
 * and come back as a numbered list, which keeps a scan of a large school inside one timeout instead
 * of several hundred. If that call fails the flags are still returned, with {@code summary} null: the
 * finding is the useful part and a phrasing service being down is no reason to withhold it.
 */
@Service
public class InsightsService {

	private static final Logger log = LoggerFactory.getLogger(InsightsService.class);

	/** Under this share of the session's marked days is {@link InsightFlag#LOW_ATTENDANCE}. */
	private static final double ATTENDANCE_FLOOR = 75;

	/** This many percentage points lost between the last two published exams is a drop. */
	private static final double DROP_POINTS = 15;

	private static final String SYSTEM = """
			You help a school principal read a list of students who need attention.

			You are given a numbered list. Each line has a student's first name and the findings the \
			school's records produced for them.

			For each line, write one short sentence in plain language that a busy principal can read \
			at a glance, saying what the concern is. Rules:
			- One sentence per student, under 25 words, no bullet points.
			- Say only what the findings say. Do not add a cause, a diagnosis, a number you were not \
			given, or a recommendation.
			- Do not judge the student or the family. "Has missed a lot of school this year" — not \
			"is not taking school seriously".
			- Return exactly one entry per input line, with the same number.
			""";

	private final AiFeature feature;
	private final AiGateway ai;
	private final SchoolClassService classes;
	private final StudentService students;
	private final ResultService results;
	private final StudentAttendanceService attendance;
	private final StudentFeeStatusProvider fees;
	private final SessionProgress sessionProgress;

	public InsightsService(AiFeature feature, AiGateway ai, SchoolClassService classes, StudentService students,
			ResultService results, StudentAttendanceService attendance, StudentFeeStatusProvider fees,
			SessionProgress sessionProgress) {
		this.feature = feature;
		this.ai = ai;
		this.classes = classes;
		this.students = students;
		this.results = results;
		this.attendance = attendance;
		this.fees = fees;
		this.sessionProgress = sessionProgress;
	}

	public List<StudentInsight> insights(InsightsRequest request) {
		feature.require();

		SessionProgress.Window window = sessionProgress.of(null);
		List<SchoolClass> scanned = request.classId() == null || request.classId().isBlank()
				? classes.list()
				: List.of(classes.get(request.classId()));
		String section = request.section() == null || request.section().isBlank()
				? null
				: request.section().trim().toUpperCase(Locale.ROOT);

		List<Flagged> flagged = new ArrayList<>();
		for (SchoolClass schoolClass : scanned) {
			flagged.addAll(flaggedIn(schoolClass, section, window));
		}

		// Most flags first. Unique id second, so two students with the same count come back in a
		// stable order rather than in whatever order the classes were read.
		List<Flagged> ranked = flagged.stream()
				.sorted(Comparator.comparingInt((Flagged entry) -> entry.flags().size()).reversed()
						.thenComparing(entry -> entry.student().uniqueId()))
				.limit(request.limitOrDefault())
				.toList();

		List<String> summaries = summarise(ranked);
		List<StudentInsight> insights = new ArrayList<>();
		for (int index = 0; index < ranked.size(); index++) {
			Flagged entry = ranked.get(index);
			insights.add(new StudentInsight(entry.student().uniqueId(), entry.student().name(), entry.className(),
					entry.student().section(), List.copyOf(entry.flags()),
					index < summaries.size() ? summaries.get(index) : null));
		}
		return insights;
	}

	// --- the flags, in code -----------------------------------------------------------------------

	private List<Flagged> flaggedIn(SchoolClass schoolClass, String section, SessionProgress.Window window) {
		List<StudentRef> roll = students.activeInClass(window.sessionId(), schoolClass.getId()).stream()
				.filter(student -> section == null || section.equals(student.section()))
				.toList();
		if (roll.isEmpty()) {
			return List.of();
		}

		// Three reads for the whole class, whatever its size.
		Map<String, AttendanceShare> shares = attendance.sharesForClass(schoolClass.getId(), window.from(),
				window.to());
		Map<String, List<StudentExamScore>> scores = results.publishedScoresByClass(window.sessionId(),
				schoolClass.getId());
		Map<String, FeeStatus> feeStatuses = fees.statusesFor(roll.stream().map(StudentRef::uniqueId).toList());

		List<Flagged> flagged = new ArrayList<>();
		for (StudentRef student : roll) {
			EnumSet<InsightFlag> flags = EnumSet.noneOf(InsightFlag.class);

			AttendanceShare share = shares.get(student.uniqueId());
			// markedDays == 0 means nobody took the register, which says nothing about the student.
			if (share != null && share.markedDays() > 0 && share.percentage() < ATTENDANCE_FLOOR) {
				flags.add(InsightFlag.LOW_ATTENDANCE);
			}

			List<StudentExamScore> history = scores.getOrDefault(student.uniqueId(), List.of());
			if (!history.isEmpty()) {
				StudentExamScore latest = history.get(history.size() - 1);
				if (!latest.failedSubjects().isEmpty()) {
					flags.add(InsightFlag.SUBJECT_FAILED);
				}
				if (history.size() >= 2
						&& history.get(history.size() - 2).percentage() - latest.percentage() >= DROP_POINTS) {
					flags.add(InsightFlag.MARKS_DROPPED);
				}
			}

			if (feeStatuses.get(student.uniqueId()) == FeeStatus.OVERDUE) {
				flags.add(InsightFlag.FEES_OVERDUE);
			}

			if (!flags.isEmpty()) {
				flagged.add(new Flagged(student, schoolClass.getName(), flags));
			}
		}
		return flagged;
	}

	private record Flagged(StudentRef student, String className, EnumSet<InsightFlag> flags) {
	}

	// --- the wording, from the model --------------------------------------------------------------

	/** One summary per entry of {@code ranked}, in the same order. Empty when the call failed. */
	private List<String> summarise(List<Flagged> ranked) {
		if (ranked.isEmpty()) {
			return List.of();
		}

		StringBuilder prompt = new StringBuilder("Students:\n");
		for (int index = 0; index < ranked.size(); index++) {
			Flagged entry = ranked.get(index);
			prompt.append(index + 1).append(". ").append(firstName(entry.student().name())).append(" — ")
					.append(String.join(", ", entry.flags().stream().map(InsightsService::describe).toList()))
					.append('\n');
		}

		Summaries summaries;
		try {
			summaries = ai.entity("summarise the at-risk list", SYSTEM, prompt.toString(), Summaries.class);
		}
		catch (AppException ex) {
			// The flags stand on their own; losing the prose is not losing the answer.
			log.warn("Returning {} at-risk students without summaries: {}", ranked.size(), ex.getMessage());
			return List.of();
		}

		String[] byPosition = new String[ranked.size()];
		for (Summary summary : summaries.summaries() == null ? List.<Summary>of() : summaries.summaries()) {
			int position = summary.number() - 1;
			if (position >= 0 && position < byPosition.length && summary.summary() != null) {
				byPosition[position] = summary.summary().trim();
			}
		}
		// Arrays.asList, not List.of: a student the model skipped stays a null, which the caller
		// renders as "no summary" rather than as a missing student.
		return Arrays.asList(byPosition);
	}

	/**
	 * The flag, in words the model can work with. Deliberately not the enum name: the prompt reads
	 * better, and the thresholds stay here where they are enforced rather than being restated.
	 */
	private static String describe(InsightFlag flag) {
		return switch (flag) {
			case LOW_ATTENDANCE -> "attendance below 75% this year";
			case MARKS_DROPPED -> "marks dropped sharply since the previous exam";
			case SUBJECT_FAILED -> "failed at least one subject in the latest exam";
			case FEES_OVERDUE -> "school fees overdue";
		};
	}

	private static String firstName(String name) {
		if (name == null || name.isBlank()) {
			return "This student";
		}
		return name.trim().split("\\s+")[0];
	}

	/** The structured shape the summaries come back in. */
	public record Summaries(List<Summary> summaries) {
	}

	/** @param number the 1-based position of the student in the list that was sent */
	public record Summary(int number, String summary) {
	}
}

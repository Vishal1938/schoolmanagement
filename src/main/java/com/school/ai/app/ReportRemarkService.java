package com.school.ai.app;

import java.util.List;

import com.school.ai.api.ReportRemarkRequest;
import com.school.ai.api.ReportRemarkResponse;
import com.school.ai.infra.AiGateway;
import com.school.attendance.app.StudentAttendanceService;
import com.school.common.exceptions.BusinessRuleException;
import com.school.exams.app.ResultService;
import com.school.exams.app.StudentExamScore;
import com.school.people.app.StudentRef;
import com.school.people.app.StudentService;
import org.springframework.stereotype.Service;

/**
 * The class teacher's remark on a report card, drafted.
 *
 * <p><strong>What the provider is told is the whole of this:</strong> a first name, the subject marks
 * and grades, how the overall percentage moved since the previous exam, and the attendance
 * percentage. No surname, no unique id, no class, no rank, no guardian, no fees, no address, no
 * date of birth. A remark can be specific about a child's work without the provider being able to
 * work out which child it is about, and a first name is what makes the sentence read like a teacher
 * wrote it.
 *
 * <p>Rank is left out deliberately rather than for want of a field: {@link StudentExamScore} does not
 * carry one. A remark that mentions where a child placed in their class is a different document from
 * one about their own progress, and not one a model should be drafting.
 *
 * <p>Nothing is saved. The remark comes back as text for a teacher to edit and own.
 */
@Service
public class ReportRemarkService {

	private static final String SYSTEM = """
			You write the class teacher's remark on a school report card in India.

			Rules:
			- Two or three sentences. No greeting, no sign-off, no bullet points, no quotation marks.
			- Write about the student in the third person, using the first name you are given.
			- Be encouraging and specific: name a subject they did well in, and refer to the actual \
			marks you were given.
			- Name exactly one area to improve. Be concrete and kind about it.
			- Mention attendance only if it is below 85%.
			- Use only the figures you are given. Do not invent marks, subjects, ranks, dates or \
			events, and never mention a rank or a position in class — you have not been told one.
			- Plain text, no markdown.
			""";

	private final AiFeature feature;
	private final AiGateway ai;
	private final ResultService results;
	private final StudentService students;
	private final StudentAttendanceService attendance;
	private final SessionProgress sessionProgress;

	public ReportRemarkService(AiFeature feature, AiGateway ai, ResultService results, StudentService students,
			StudentAttendanceService attendance, SessionProgress sessionProgress) {
		this.feature = feature;
		this.ai = ai;
		this.results = results;
		this.students = students;
		this.attendance = attendance;
		this.sessionProgress = sessionProgress;
	}

	public ReportRemarkResponse remark(ReportRemarkRequest request) {
		feature.require();

		// requireRef is the 404 on an unknown student; publishedScores then enforces the object-level
		// rule, so a teacher cannot reach a student the exams module would not show them.
		StudentRef student = students.requireRef(request.studentUniqueId());
		String sessionId = results.sessionOfExam(request.examId());
		List<StudentExamScore> history = results.publishedScores(student.uniqueId(), sessionId);

		int index = indexOf(history, request.examId());
		if (index < 0) {
			throw new BusinessRuleException(student.name() + " has no published result in that exam. The exam has to "
					+ "be published, and has to be one their current class sits.");
		}
		StudentExamScore exam = history.get(index);
		StudentExamScore previous = index > 0 ? history.get(index - 1) : null;

		SessionProgress.Window window = sessionProgress.of(sessionId);
		double attendancePercentage = attendance
				.shareFor(student.uniqueId(), window.from(), window.to())
				.percentage();
		Double trend = previous == null ? null
				: Math.round((exam.percentage() - previous.percentage()) * 100) / 100.0;

		String remark = unquote(ai.text("write a report-card remark",
				SYSTEM, List.of(), userPrompt(firstName(student.name()), exam, previous, trend,
						attendancePercentage)));

		return new ReportRemarkResponse(student.uniqueId(), exam.examId(), exam.examName(), remark,
				new ReportRemarkResponse.Basis(exam.percentage(), exam.grade(),
						previous == null ? null : previous.examName(), trend, attendancePercentage));
	}

	private static String userPrompt(String firstName, StudentExamScore exam, StudentExamScore previous,
			Double trend, double attendance) {
		StringBuilder prompt = new StringBuilder()
				.append("First name: ").append(firstName).append('\n')
				.append("Subjects in this exam:\n");
		for (StudentExamScore.SubjectScore subject : exam.subjects()) {
			prompt.append("- ").append(subject.subjectName()).append(": ");
			if (subject.absent()) {
				prompt.append("absent");
			}
			else if (subject.marks() == null) {
				prompt.append("not marked");
			}
			else {
				prompt.append(subject.marks()).append(" out of ").append(subject.maxMarks());
				if (subject.grade() != null) {
					prompt.append(", grade ").append(subject.grade());
				}
				if (!subject.passed()) {
					prompt.append(", below the pass mark");
				}
			}
			prompt.append('\n');
		}
		prompt.append("Overall: ").append(exam.percentage()).append('%');
		if (exam.grade() != null) {
			prompt.append(", grade ").append(exam.grade());
		}
		prompt.append('\n');
		if (previous == null || trend == null) {
			prompt.append("Trend: this is their first published exam of the year, so there is nothing to "
					+ "compare it with. Do not mention improvement or decline.\n");
		}
		else {
			prompt.append("Trend: ")
					.append(trend >= 0 ? "up " : "down ")
					.append(Math.abs(trend))
					.append(" percentage points since the previous exam\n");
		}
		prompt.append("Attendance so far this year: ").append(attendance).append("%\n");
		return prompt.toString();
	}

	private static int indexOf(List<StudentExamScore> history, String examId) {
		for (int index = 0; index < history.size(); index++) {
			if (history.get(index).examId().equals(examId)) {
				return index;
			}
		}
		return -1;
	}

	/**
	 * The name as a teacher would say it. Names in this part of the world are written in every
	 * possible order, so this takes the first word and nothing cleverer — which is right far more
	 * often than any rule about surnames would be, and is only ever used inside a prompt.
	 */
	private static String firstName(String name) {
		if (name == null || name.isBlank()) {
			return "The student";
		}
		String[] words = name.trim().split("\\s+");
		return words[0];
	}

	/** Models sometimes wrap a one-line answer in quotes, which would end up on the report card. */
	private static String unquote(String text) {
		String trimmed = text.trim();
		return trimmed.length() > 1 && trimmed.startsWith("\"") && trimmed.endsWith("\"")
				? trimmed.substring(1, trimmed.length() - 1).trim()
				: trimmed;
	}
}

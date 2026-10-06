package com.school.exams.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.school.exams.domain.Exam;
import com.school.exams.domain.ExamStatus;

/** An exam as the API returns it, with subject names resolved so the UI does not have to join. */
public record ExamResponse(
		String id,
		String name,
		String sessionId,
		List<String> classIds,
		List<Paper> schedule,
		ExamStatus status) {

	public record Paper(String subjectId, String subjectName, LocalDate date, int maxMarks, int passMarks) {
	}

	/** @param subjectNames subject id to name; a missing entry leaves the name null rather than failing */
	public static ExamResponse of(Exam exam, Map<String, String> subjectNames) {
		List<Paper> schedule = exam.getSchedule() == null ? List.of() : exam.getSchedule().stream()
				.map(paper -> new Paper(paper.subjectId(), subjectNames.get(paper.subjectId()), paper.date(),
						paper.maxMarks(), paper.passMarks()))
				.toList();
		return new ExamResponse(exam.getId(), exam.getName(), exam.getSessionId(),
				exam.getClassIds() == null ? List.of() : exam.getClassIds(), schedule, exam.getStatus());
	}
}

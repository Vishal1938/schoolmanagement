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
 * A quiz set for one class.
 *
 * <p>The questions, including their answer key, are embedded — see {@link QuizQuestion}. Nothing
 * outside this module ever sees this document: the teacher's view, the student's view and the answer
 * key are three different DTOs built in {@code QuizService} and {@code QuizAttemptService}.
 *
 * <p><strong>Two windows, not one.</strong> {@link #startAt}–{@link #endAt} is when the quiz may be
 * <em>started</em>; {@link #timeLimitMinutes} is how long one student then gets. An attempt's own
 * deadline is the earlier of the two, so a student who starts five minutes before the quiz closes
 * gets five minutes and not the full limit.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(Quiz.COLLECTION)
// The students' list: published quizzes for one class, soonest first.
@CompoundIndex(name = "quizzes_class_idx", def = "{'classId': 1, 'status': 1, 'startAt': 1}")
// A teacher's own quizzes, which is the only list a teacher is shown.
@CompoundIndex(name = "quizzes_creator_idx", def = "{'createdBy': 1, 'createdAt': -1}")
public class Quiz {

	public static final String COLLECTION = "quizzes";

	@Id
	private String id;

	private String title;

	/** Optional. Instructions, syllabus covered — whatever the teacher wants read before starting. */
	private String description;

	private String subjectId;

	private String classId;

	/**
	 * The sections this quiz is set for, upper-case. <strong>Empty means every section of the
	 * class</strong>, which is both the common case and the default; it is stored empty rather than
	 * expanded, so a section added to the class later is included without touching the quiz.
	 */
	private List<String> sections;

	/** How long one attempt may last, in minutes. */
	private int timeLimitMinutes;

	/** When the quiz opens. Null while it is a draft; publication requires it. */
	private Instant startAt;

	/** When it closes to new attempts. Null while it is a draft; publication requires it. */
	private Instant endAt;

	/** How many times one student may sit it. At least 1. */
	private int maxAttempts;

	/** Whether each attempt gets its own question order. Options are never reordered. */
	private boolean shuffleQuestions;

	/** Whether a submission comes back with the key and the explanations. */
	private boolean showAnswersAfterSubmit;

	private QuizStatus status;

	/** Includes the answer key; never handed to a student. */
	private List<QuizQuestion> questions;

	/** The uniqueId of the teacher or admin who wrote it. Never reassigned by an edit. */
	private String createdBy;

	/** Copied at write time, so results still name the author after they leave the school. */
	private String createdByName;

	private Instant createdAt;

	private Instant updatedAt;

	/** When it was published, or null. */
	private Instant publishedAt;

	/** When it was closed, or null. */
	private Instant closedAt;

	/** The sum of every question's marks: what a perfect attempt scores. */
	public int totalMarks() {
		return questions == null ? 0 : questions.stream().mapToInt(QuizQuestion::marks).sum();
	}

	public int questionCount() {
		return questions == null ? 0 : questions.size();
	}

	/**
	 * Whether this quiz is set for the given section of its class. A quiz with no sections is set for
	 * all of them.
	 */
	public boolean coversSection(String section) {
		return sections == null || sections.isEmpty() || sections.contains(section);
	}
}

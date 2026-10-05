package com.school.exams.domain;

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
 * An exam — "Half Yearly" — sat by one or more classes in one session.
 *
 * <p>One exam spans several classes because that is how a school runs them: the half-yearly is one
 * event with one set of dates, even though Class 1 and Class 5 sit different papers. The schedule
 * is therefore per subject, and which subjects a given class actually sits follows from the
 * subjects on that class.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(Exam.COLLECTION)
// The list endpoint: exams of a session, optionally for one class. classIds is an array, so this is
// a multikey index and still serves the ?classId= filter.
@CompoundIndex(name = "exams_session_class_idx", def = "{'sessionId': 1, 'classIds': 1}")
public class Exam {

	public static final String COLLECTION = "exams";

	@Id
	private String id;

	/** What the school calls it: "Half Yearly", "Unit Test 1". */
	private String name;

	/** The academic session this exam belongs to. Taken from AcademicContext, never from the client. */
	private String sessionId;

	/** Which classes sit it. */
	private List<String> classIds;

	/** One entry per paper. A subject not in here is not part of this exam. */
	private List<ExamSubject> schedule;

	private ExamStatus status;

	private Instant createdAt;

	private Instant updatedAt;
}

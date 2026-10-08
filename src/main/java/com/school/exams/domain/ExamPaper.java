package com.school.exams.domain;

import java.time.Instant;
import java.util.Comparator;
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
 * A question paper in the vault: one paper per (exam, class, subject), with every version of it.
 *
 * <p>The vault exists because a question paper is the one document in this system that is secret
 * until a date and then ordinary. {@link #releaseAt} is that date, and it is enforced on download
 * rather than by hiding the record: a teacher is told the paper exists and when it opens, which is
 * what they need to plan, while the bytes stay unreachable until then.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(ExamPaper.COLLECTION)
// Unique: one paper per subject per class per exam. A second upload for the same triple is a new
// version of that paper, never a second document — this index is what makes that true under a race.
@CompoundIndex(name = "exam_papers_exam_class_subject_idx",
		def = "{'examId': 1, 'classId': 1, 'subjectId': 1}", unique = true)
// The list endpoint filtered by class alone; (examId, classId) is already served by the index above.
@CompoundIndex(name = "exam_papers_class_exam_idx", def = "{'classId': 1, 'examId': 1}")
public class ExamPaper {

	public static final String COLLECTION = "exam_papers";

	@Id
	private String id;

	private String examId;

	private String classId;

	private String subjectId;

	/** What the vault lists it as, e.g. "Half Yearly — Maths". Defaulted from the exam and subject. */
	private String title;

	/**
	 * When teachers other than the uploader may download it. In the future when the paper is first
	 * uploaded; movable afterwards by an admin, or by the uploader while it is still locked.
	 */
	private Instant releaseAt;

	/** Append-only, oldest first. Never empty: a paper exists because a file was uploaded. */
	private List<PaperVersion> versions;

	/** {@code uniqueId} of whoever uploaded version 1. They keep access to it whatever the clock says. */
	private String createdBy;

	private Instant createdAt;

	private Instant updatedAt;

	/** The version a download gets when none is asked for. */
	public PaperVersion latestVersion() {
		return versions.stream().max(Comparator.comparingInt(PaperVersion::versionNo)).orElseThrow();
	}
}

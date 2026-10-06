package com.school.exams.domain;

import java.time.LocalDate;

/**
 * One paper in an exam's schedule.
 *
 * <p>{@code maxMarks} and {@code passMarks} live here rather than on the subject, because the same
 * subject is worth 80 in the half-yearly and 100 in the finals.
 *
 * @param date      when the paper is sat
 * @param maxMarks  the paper is out of this
 * @param passMarks below this is a fail in this subject
 */
public record ExamSubject(String subjectId, LocalDate date, int maxMarks, int passMarks) {
}

package com.school.exams.api;

import com.school.exams.domain.ExamStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PUT /exams/{id}/status}.
 *
 * <p>Moving to {@code PUBLISHED} fails with 422 and a {@code missing} array if any student is
 * without a mark in any subject of the exam.
 */
public record ExamStatusRequest(@NotNull ExamStatus status) {
}

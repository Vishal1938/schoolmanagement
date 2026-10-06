package com.school.schoolconfig.domain;

/**
 * One row of the grading scheme: the percentage range a grade covers, inclusive at both ends.
 *
 * @param grade          the label printed on the report card, e.g. {@code A1}
 * @param minPercentage  lowest percentage that earns this grade
 * @param maxPercentage  highest percentage that earns this grade
 * @param remark         optional wording printed beside the grade, e.g. {@code Outstanding}
 */
public record GradeBand(String grade, int minPercentage, int maxPercentage, String remark) {

	public boolean covers(int percentage) {
		return percentage >= minPercentage && percentage <= maxPercentage;
	}
}

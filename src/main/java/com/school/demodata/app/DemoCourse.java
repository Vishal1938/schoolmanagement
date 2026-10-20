package com.school.demodata.app;

import java.util.List;

/**
 * One course of the demonstration college, and what it charges. All amounts are paise.
 *
 * <p>The semester <em>numbers</em> are derived rather than listed: a course named "Year N" is in its
 * {@code 2N-1}th and {@code 2N}th semesters of the programme, so the second year of the BA is
 * Semesters 3 and 4. Those numbers are what the installments are named after, which is what makes
 * "Semester 3" mean the same thing on an invoice, a register column and a receipt.
 *
 * @param courseName      the class name to look up, exactly as the office created it
 * @param year            which year of the programme this course is
 * @param semesterFee     per semester, charged twice
 * @param admissionFee    0 for every year but the first, which is the only year anybody is admitted in
 * @param examFee         per semester, charged as two one-off fees with their own due dates
 * @param partialFirst    what the "partial" pattern pays in August
 * @param partialSecond   and what it pays in September, still leaving the semester short
 */
record DemoCourse(String courseName, int year, long semesterFee, long admissionFee, long examFee,
		long partialFirst, long partialSecond) {

	/** Rupees, as the specification states them, turned into paise exactly once. */
	private static final long RUPEE = 100L;

	/**
	 * The five courses, in the order the seeder walks them.
	 *
	 * <p>The BA charges Year 3 more than Years 1 and 2, and the BTC charges roughly three times the
	 * BA — which is the point of having both: a register, a defaulters list and a collection report
	 * that all look plausible rather than uniform.
	 */
	static final List<DemoCourse> ALL = List.of(
			new DemoCourse("BA - Year 1", 1, 4_800 * RUPEE, 3_000 * RUPEE, 1_200 * RUPEE,
					2_400 * RUPEE, 1_000 * RUPEE),
			new DemoCourse("BA - Year 2", 2, 4_800 * RUPEE, 0L, 1_200 * RUPEE,
					2_400 * RUPEE, 1_000 * RUPEE),
			new DemoCourse("BA - Year 3", 3, 5_400 * RUPEE, 0L, 1_500 * RUPEE,
					2_400 * RUPEE, 1_000 * RUPEE),
			new DemoCourse("BTC - Year 1", 1, 15_000 * RUPEE, 10_000 * RUPEE, 2_000 * RUPEE,
					7_500 * RUPEE, 3_000 * RUPEE),
			new DemoCourse("BTC - Year 2", 2, 15_000 * RUPEE, 0L, 2_000 * RUPEE,
					7_500 * RUPEE, 3_000 * RUPEE));

	/** The semester of the programme this course's first half is: 1, 3 or 5. */
	int firstSemester() {
		return 2 * year - 1;
	}

	/** And its second half: 2, 4 or 6. */
	int secondSemester() {
		return 2 * year;
	}

	/** Only the first year of a programme is admitted into, so only it is charged an admission fee. */
	boolean chargesAdmissionFee() {
		return admissionFee > 0L;
	}

	/** {@code "Semester 3"} — the installment name, and therefore an identifier. */
	String firstSemesterName() {
		return semesterName(firstSemester());
	}

	String secondSemesterName() {
		return semesterName(secondSemester());
	}

	/** {@code "Exam fee (Sem 3)"}, which is unique within the structure as an installment name must be. */
	String firstExamFeeName() {
		return examFeeName(firstSemester());
	}

	String secondExamFeeName() {
		return examFeeName(secondSemester());
	}

	static String semesterName(int number) {
		return "Semester " + number;
	}

	static String examFeeName(int semesterNumber) {
		return "Exam fee (Sem " + semesterNumber + ")";
	}
}

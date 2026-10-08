package com.school.quiz.domain;

/**
 * What a question asks for, which is also what decides how it is graded.
 *
 * <p>All three are option-based and all three are graded the same way — an exact match between the
 * options chosen and the options keyed as correct. The type is therefore not a grading rule but a
 * <em>structural</em> one: it says how many options a question must carry and how many of them may be
 * correct, and it tells the frontend whether to draw radio buttons or checkboxes.
 */
public enum QuestionType {

	/** Several options, exactly one correct. Radio buttons. */
	MCQ_SINGLE,

	/**
	 * Several options, one or more correct. Checkboxes. Graded all-or-nothing: a student who picks two
	 * of three correct options scores zero, because partial credit on a multi-select is a policy
	 * decision no school has made here.
	 */
	MCQ_MULTI,

	/**
	 * Exactly two options, exactly one correct. Kept distinct from {@link #MCQ_SINGLE} with two options
	 * so the frontend can render it as a true/false toggle; the option text is still the quiz author's,
	 * so it works for "Yes/No" and for other languages.
	 */
	TRUE_FALSE
}

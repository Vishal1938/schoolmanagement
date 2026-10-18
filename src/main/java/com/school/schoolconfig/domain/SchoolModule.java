package com.school.schoolconfig.domain;

/**
 * One switchable module, as the rest of the application names it.
 *
 * <p>This is the enum other modules hold — {@code moduleGate.require(SchoolModule.EXAMS)} — so that
 * no caller has to know which field of {@link Modules} it is reading, and so that adding a module
 * means adding one field and one constant rather than threading a boolean through.
 *
 * <p>A null {@link Modules} reads as everything on, which is what makes a deployment configured
 * before any of this existed behave exactly as it did.
 */
public enum SchoolModule {

	EXAMS,

	QUIZZES,

	EXAM_PAPERS,

	NOTICES,

	STUDENT_LOGIN,

	ONLINE_PAYMENTS,

	LANDING_PAGE,

	AI;

	/** Whether this module is on in the given set. */
	public boolean isOn(Modules modules) {
		Modules effective = modules == null ? Modules.ALL_ON : modules;
		return switch (this) {
			case EXAMS -> effective.exams();
			case QUIZZES -> effective.quizzes();
			case EXAM_PAPERS -> effective.examPapers();
			case NOTICES -> effective.notices();
			case STUDENT_LOGIN -> effective.studentLogin();
			case ONLINE_PAYMENTS -> effective.onlinePayments();
			case LANDING_PAGE -> effective.landingPage();
			case AI -> effective.ai();
		};
	}
}

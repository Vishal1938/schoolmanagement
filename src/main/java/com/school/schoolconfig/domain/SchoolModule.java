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

	STUDENT_ATTENDANCE,

	STUDENT_LOGIN,

	ONLINE_PAYMENTS,

	LANDING_PAGE,

	AI;

	/** Whether this module is on in the given set, where anything it leaves unsaid is on. */
	public boolean isOn(Modules modules) {
		Modules effective = modules == null ? Modules.ALL_ON : modules.withDefaults();
		return switch (this) {
			case EXAMS -> effective.exams();
			case QUIZZES -> effective.quizzes();
			case EXAM_PAPERS -> effective.examPapers();
			case NOTICES -> effective.notices();
			case STUDENT_ATTENDANCE -> effective.studentAttendance();
			case STUDENT_LOGIN -> effective.studentLogin();
			case ONLINE_PAYMENTS -> effective.onlinePayments();
			case LANDING_PAGE -> effective.landingPage();
			case AI -> effective.ai();
		};
	}
}

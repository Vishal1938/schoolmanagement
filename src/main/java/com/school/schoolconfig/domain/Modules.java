package com.school.schoolconfig.domain;

/**
 * Which optional parts of the product this deployment has switched on.
 *
 * <p><strong>Every one defaults to true.</strong> A deployment that was configured before this
 * existed has no {@code modules} subdocument at all, which reads as {@link #ALL_ON} — so the
 * behaviour of a school already running does not change the day this ships. Switching something off
 * is always a deliberate act by an administrator.
 *
 * <p>An endpoint behind a module that is off answers <strong>404 {@code FEATURE_DISABLED}</strong>,
 * not 403 and not 503: see {@link com.school.schoolconfig.app.ModuleGate}. The whole set is published
 * on {@code GET /public/school}, so a frontend hides the navigation entry rather than calling and
 * handling the error.
 *
 * @param exams          exams, marks and report cards
 * @param quizzes        online quizzes and attempts
 * @param examPapers     the exam paper vault, which is separate from {@code exams}: an institution
 *                       can run exams on paper and still not want an upload vault
 * @param notices        notices, including the public ones the landing page lists
 * @param studentLogin   whether students get a login at all. Off means admission and import create no
 *                       user and issue no temporary password, and an existing student account is
 *                       refused at login — see {@code AuthService}
 * @param onlinePayments the gateway checkout. Off leaves the counter (offline) payment flow working,
 *                       which is the point: a college that only takes money at the desk
 * @param landingPage    the public marketing content on {@code GET /public/school}. Off keeps the
 *                       identity, the vocabulary and this set — a client still needs those to render
 *                       the login screen — and omits the landing block entirely
 * @param ai             the AI features. Gated by {@code APP_FEATURES_AI} as well: this one is the
 *                       institution's choice, that one is the deployment's, and both have to say yes
 */
public record Modules(
		boolean exams,
		boolean quizzes,
		boolean examPapers,
		boolean notices,
		boolean studentLogin,
		boolean onlinePayments,
		boolean landingPage,
		boolean ai) {

	/** The default, and what a missing {@code modules} subdocument means. */
	public static final Modules ALL_ON = new Modules(true, true, true, true, true, true, true, true);
}

package com.school.schoolconfig.domain;

/**
 * Which optional parts of the product this deployment has switched on.
 *
 * <p><strong>Every one defaults to true.</strong> A deployment that was configured before this
 * existed has no {@code modules} subdocument at all, which reads as {@link #ALL_ON} — so the
 * behaviour of a school already running does not change the day this ships. Switching something off
 * is always a deliberate act by an administrator.
 *
 * <p>Each switch is a {@link Boolean} rather than a primitive, and that is load-bearing. A
 * subdocument stored before a switch was added has no field for it, and Spring Data cannot pass the
 * resulting {@code null} to a primitive record component — it throws
 * {@code IllegalArgumentException: Parameter studentAttendance must not be null} while instantiating
 * the one configuration document, which fails every request that reads it, not merely the gated ones.
 * {@code studentAttendance} was added to a running product and did exactly that. Nullable components
 * plus {@link #withDefaults()} let a flag be added over live data without a migration, the same way
 * {@link Terms} absorbs a word it was stored without.
 *
 * <p>So read these through {@link #withDefaults()} — or, better, through
 * {@code SchoolConfig.resolvedModules()} and {@link SchoolModule#isOn}, which both apply it. A switch
 * read straight off a stored instance can be null.
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
 * @param studentAttendance the daily class register. Off means an institution that keeps no student
 *                       attendance at all — a college running on subject-wise registers it holds
 *                       elsewhere. <strong>Employee attendance is not covered by it</strong> and stays
 *                       on either way, because payroll prorates salaries from it
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
 * @param accounts       the expense book: money accounts, categories, entries and transfers. Off
 *                       means an institution that keeps its accounts in Tally or in a ledger and
 *                       wants nothing here. The automatic entries stop with it, which is why
 *                       {@code POST /accounts/backfill} exists to catch up afterwards
 */
public record Modules(
		Boolean exams,
		Boolean quizzes,
		Boolean examPapers,
		Boolean notices,
		Boolean studentAttendance,
		Boolean studentLogin,
		Boolean onlinePayments,
		Boolean landingPage,
		Boolean ai,
		Boolean accounts) {

	/** The default, and what a missing {@code modules} subdocument means. */
	public static final Modules ALL_ON =
			new Modules(true, true, true, true, true, true, true, true, true, true);

	/**
	 * This set with every switch it does not mention turned on.
	 *
	 * <p>Applied on the way out, so a subdocument written before a switch existed answers for it. Only
	 * a stored {@code false} turns anything off; absent means the deployment never expressed a view,
	 * and the documented default for that is on.
	 */
	public Modules withDefaults() {
		return new Modules(
				orOn(exams),
				orOn(quizzes),
				orOn(examPapers),
				orOn(notices),
				orOn(studentAttendance),
				orOn(studentLogin),
				orOn(onlinePayments),
				orOn(landingPage),
				orOn(ai),
				orOn(accounts));
	}

	private static Boolean orOn(Boolean value) {
		return value == null ? Boolean.TRUE : value;
	}
}

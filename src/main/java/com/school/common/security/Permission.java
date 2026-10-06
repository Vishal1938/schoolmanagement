package com.school.common.security;

/**
 * Every permission the API authorises on. This enum is the closed vocabulary referenced by
 * API_CONTRACT.md §2: the list is returned in {@code /auth/me} and carried in the access token, and the
 * frontend decides what to show from it rather than from the role.
 *
 * <p>The enum constant name <em>is</em> the Spring Security authority, so
 * {@code hasAuthority('FEE_READ_FULL')} matches {@link #FEE_READ_FULL}. Write those expressions with
 * the constants in {@link HasPermission} rather than by hand.
 *
 * <p>Permissions are coarse — "may enter marks" — while object-level rules such as "only for a class
 * this teacher is assigned to" belong in the service, as CLAUDE.md rule 1 requires. Later tasks add
 * the permissions they need; nothing is renamed, because a rename silently widens or removes access.
 */
public enum Permission {

	// --- people (B5, B6, B7) ----------------------------------------------------------------------

	/** A student record including fees, guardians and contact details: administrators only. */
	STUDENT_READ_FULL,

	/** The teacher-safe student projection: no fee amounts and no payment history. */
	STUDENT_READ_BASIC,

	STUDENT_WRITE,

	EMPLOYEE_READ,

	EMPLOYEE_WRITE,

	// --- attendance (B8) --------------------------------------------------------------------------

	ATTENDANCE_MARK_STUDENT,

	ATTENDANCE_MARK_EMPLOYEE,

	/**
	 * Correcting a register after the edit window has closed. A teacher has
	 * {@code ATTENDANCE_MARK_STUDENT} but not this, which is exactly what the window means; the
	 * office keeps it so that a genuine mistake is still fixable months later.
	 */
	ATTENDANCE_CORRECT_ANY,

	/**
	 * Adding and removing holidays. Reading them needs no permission — a holiday is public knowledge
	 * and every calendar in the app shows them.
	 */
	HOLIDAY_MANAGE,

	// --- exams and marks (B9, B14) ----------------------------------------------------------------

	MARKS_WRITE,

	/** A student reading their own marks and report cards. */
	MARKS_READ_SELF,

	EXAM_MANAGE,

	EXAM_PAPER_UPLOAD,

	/** Download from the exam-paper vault, which is refused before {@code releaseAt} for teachers. */
	EXAM_PAPER_READ,

	// --- fees and payments (B11, B12) -------------------------------------------------------------

	FEE_MANAGE,

	/** Invoices and amounts for any student. */
	FEE_READ_FULL,

	/** Only the derived status — PAID, DUE, PARTIAL, OVERDUE — with no amounts. */
	FEE_READ_STATUS,

	/** Paying one's own invoices online. */
	FEE_PAY_SELF,

	// --- payroll (B13) ----------------------------------------------------------------------------

	PAYROLL_MANAGE,

	PAYROLL_READ_SELF,

	// --- notices (B10) ----------------------------------------------------------------------------

	NOTICE_WRITE_ALL,

	/** A notice limited to the classes the author teaches. */
	NOTICE_WRITE_CLASS,

	NOTICE_READ,

	// --- quizzes (B15) ----------------------------------------------------------------------------

	QUIZ_MANAGE,

	QUIZ_ATTEMPT,

	// --- academics (B4) ---------------------------------------------------------------------------

	/**
	 * Creating and editing sessions, classes, subjects and teaching assignments. Reading them needs no
	 * permission at all: every logged-in user has to know what class 5-B is.
	 */
	ACADEMICS_MANAGE,

	// --- administration ---------------------------------------------------------------------------

	SCHOOL_CONFIG_MANAGE,

	DASHBOARD_ADMIN,

	/** Reading the audit trail: it names who did what, so it is for administrators only. */
	AUDIT_READ,

	/** The AI endpoints (B18), which are additionally gated by {@code app.features.ai}. */
	AI_USE
}

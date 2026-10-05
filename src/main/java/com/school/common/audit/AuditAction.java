package com.school.common.audit;

/**
 * The closed vocabulary of audited actions. An enum rather than free-text strings so the trail can be
 * queried and reviewed, and so a typo cannot invent a new action that nobody searches for.
 *
 * <p>Each task adds the actions it introduces. CLAUDE.md requires an entry for every create, update
 * and delete on marks, attendance, fees, payments, payroll and exam-paper access.
 */
public enum AuditAction {

	// --- authentication (wired in B2) ------------------------------------------------------------

	/** A successful login. Entity is the user; no credential material is recorded. */
	LOGIN_SUCCESS,

	/** A rejected login. The entity id is the uniqueId that was attempted, never the password. */
	LOGIN_FAILURE,

	/** The failed-attempt limit was reached and the account was locked. */
	ACCOUNT_LOCKED,

	/** A user changed their own password. Only the fact is recorded. */
	PASSWORD_CHANGED,

	/** An admin issued a new temporary password. The password itself is never recorded. */
	PASSWORD_RESET,

	/** A refresh token was revoked and its cookie cleared. */
	LOGOUT,

	/** A refresh token was presented twice, so its whole family was revoked. */
	REFRESH_TOKEN_REUSE_REVOKED,

	/** A login was issued — by the bootstrap initializer, the local seeder, or an admin adding a person. */
	USER_CREATED,

	// --- school configuration (B1) --------------------------------------------------------------

	/** First-boot seeding of {@code school_config}. */
	SCHOOL_CONFIG_SEEDED,

	/** An admin replaced the school configuration. */
	SCHOOL_CONFIG_UPDATED,

	// --- academics (B4) -------------------------------------------------------------------------

	SESSION_CREATED,

	/** A different session became the current one; every module's default context moves with it. */
	SESSION_ACTIVATED,

	CLASS_CREATED,

	CLASS_UPDATED,

	/** Class teachers and subject teachers were replaced for a class. */
	CLASS_ASSIGNMENTS_UPDATED,

	SUBJECT_CREATED,

	SUBJECT_UPDATED,

	/** Local-profile seeding of sessions, classes and subjects. */
	ACADEMICS_SEEDED,

	// --- students (B5) --------------------------------------------------------------------------

	/** A student was admitted, together with their login. */
	STUDENT_CREATED,

	STUDENT_UPDATED,

	/** ACTIVE, LEFT or ALUMNI. Separate from a plain update, because it is what reports filter on. */
	STUDENT_STATUS_CHANGED,

	// --- employees (B6) -------------------------------------------------------------------------

	/** A teacher or staff member was taken on, with their login where they get one. */
	EMPLOYEE_CREATED,

	/** Bank account numbers are redacted by AuditSanitizer before they reach the entry. */
	EMPLOYEE_UPDATED,

	/** ACTIVE or LEFT. The login is enabled or disabled in the same call. */
	EMPLOYEE_STATUS_CHANGED,

	/** Local-profile back-filling of employee records for logins that already existed. */
	EMPLOYEES_SEEDED,

	// --- attendance (B8) ------------------------------------------------------------------------

	/** A class-section register was submitted or corrected. Before and after are the whole entry lists. */
	STUDENT_ATTENDANCE_MARKED,

	/** A day's staff register was submitted or corrected. */
	EMPLOYEE_ATTENDANCE_MARKED,

	HOLIDAY_CREATED,

	HOLIDAY_DELETED,

	// --- exams and marks (B9) -------------------------------------------------------------------

	EXAM_CREATED,

	EXAM_UPDATED,

	/** DRAFT, MARKS_ENTRY or PUBLISHED. Publishing is what makes results visible to students. */
	EXAM_STATUS_CHANGED,

	/** A subject's marks were entered or corrected for one class-section. */
	MARKS_ENTERED,

	// --- notices (B10) --------------------------------------------------------------------------

	NOTICE_CREATED,

	NOTICE_UPDATED,

	/** The notice as it was is kept in {@code before}, so a deletion by mistake is recoverable. */
	NOTICE_DELETED,

	// --- fees (B11) -----------------------------------------------------------------------------

	FEE_HEAD_CREATED,

	/** A rename, or a head retired with {@code active: false}. Heads are never deleted. */
	FEE_HEAD_UPDATED,

	FEE_STRUCTURE_CREATED,

	FEE_STRUCTURE_UPDATED,

	/**
	 * A {@code generate-invoices} run. The entity is the structure, and {@code after} holds the
	 * counts — how many students, how many written and how many already existed — rather than every
	 * invoice, which have their own entries.
	 */
	FEE_INVOICES_GENERATED,

	/**
	 * One invoice's concession and {@code netAmount} were rewritten. Before and after are the whole
	 * invoice, because this is money a family has been quoted.
	 */
	FEE_INVOICE_RECALCULATED,

	FEE_CONCESSION_CREATED,

	/** A concession was pulled back over invoices already raised, with the counts in {@code after}. */
	FEE_CONCESSION_APPLIED,

	/**
	 * A payment landed on one invoice. One entry per invoice, with the whole invoice before and after,
	 * so "who marked this paid, and on what evidence" is answerable per invoice and not only per
	 * receipt.
	 */
	FEE_PAYMENT_APPLIED,

	// --- payments (B11 part 2, B12) -------------------------------------------------------------

	/**
	 * Money received. {@code after} is the whole payment including its allocations and receipt
	 * number; there is no update or delete counterpart, because payments are append-only.
	 */
	PAYMENT_RECORDED,

	/**
	 * A receipt PDF was rendered and stored. Written once per receipt, on the first download, which is
	 * the moment the document a family keeps comes into existence.
	 */
	RECEIPT_GENERATED,

	/**
	 * A gateway order was created. No money has moved yet, but the amount the family is about to be
	 * charged was decided here, so {@code after} is the payment as it was written.
	 */
	PAYMENT_ORDER_CREATED,

	/**
	 * A gateway payment was settled: captured, allocated and receipted. {@code before} is the created
	 * order and {@code after} the settled payment, so the whole of what the gateway's callback caused
	 * is one entry.
	 */
	PAYMENT_CAPTURED,

	/**
	 * A gateway payment did not complete — abandoned at the checkout, declined, refunded, or refused
	 * here because its signature did not verify. The detail is the reason, never a signature.
	 */
	PAYMENT_FAILED,

	/**
	 * A capture settled for less than the gateway took, because the invoices no longer needed all of
	 * it. Recorded separately from {@link #PAYMENT_CAPTURED} because it is the one thing in this flow
	 * that needs a human: somebody owes the family a refund.
	 */
	PAYMENT_UNALLOCATED,

	// --- payroll (B13) --------------------------------------------------------------------------

	/**
	 * A new version of an employee's salary structure. {@code before} is the version it supersedes and
	 * {@code after} the one just written — nothing is overwritten, so this records what changed about
	 * what somebody is paid, which is the question an auditor actually asks.
	 */
	SALARY_STRUCTURE_VERSIONED,

	/** An advance was handed over. There is no update or delete: recovery is the only thing that moves. */
	SALARY_ADVANCE_CREATED,

	/**
	 * An advance's balance moved because a salary was paid. Separate from the payment entry because it
	 * is the only thing that reduces what an employee owes the school.
	 */
	SALARY_ADVANCE_RECOVERED,

	/**
	 * A payroll run. The entity is the month, and {@code after} holds the counts — how many records
	 * were written, how many employees were skipped, and who had no structure — rather than every
	 * record, which have their own entries.
	 */
	PAYROLL_RUN,

	/** One employee's salary for one month was computed. {@code after} is the whole record. */
	PAYROLL_RECORD_CREATED,

	/** A salary was paid out, with the mode, reference and date it went out on. */
	PAYROLL_RECORD_PAID,

	/**
	 * A PENDING record was thrown away so the month could be re-run, usually after a structure was
	 * corrected. {@code before} is the whole record, so what was discarded is recoverable. A PAID
	 * record can never reach here.
	 */
	PAYROLL_RECORD_DELETED
}

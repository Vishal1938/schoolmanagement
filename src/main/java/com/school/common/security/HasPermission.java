package com.school.common.security;

/**
 * The {@code @PreAuthorize} expression for each {@link Permission}, so no controller spells a
 * permission out as a bare string.
 *
 * <pre>{@code
 * @PreAuthorize(HasPermission.MARKS_WRITE)
 * }</pre>
 *
 * <p>Annotation values must be compile-time constants, which rules out deriving these from the enum at
 * runtime. The invariant to keep is therefore simple and local: <strong>the field name matches the
 * {@link Permission} constant, and the expression names the same permission.</strong> Both are on the
 * same line, so a mismatch is visible where it is made.
 */
public final class HasPermission {

	public static final String STUDENT_READ_FULL = "hasAuthority('STUDENT_READ_FULL')";
	public static final String STUDENT_READ_BASIC = "hasAuthority('STUDENT_READ_BASIC')";
	public static final String STUDENT_WRITE = "hasAuthority('STUDENT_WRITE')";
	public static final String EMPLOYEE_READ = "hasAuthority('EMPLOYEE_READ')";
	public static final String EMPLOYEE_WRITE = "hasAuthority('EMPLOYEE_WRITE')";
	public static final String IMPORT_CREDENTIALS_READ = "hasAuthority('IMPORT_CREDENTIALS_READ')";
	public static final String ATTENDANCE_MARK_STUDENT = "hasAuthority('ATTENDANCE_MARK_STUDENT')";
	public static final String ATTENDANCE_MARK_EMPLOYEE = "hasAuthority('ATTENDANCE_MARK_EMPLOYEE')";
	public static final String ATTENDANCE_CORRECT_ANY = "hasAuthority('ATTENDANCE_CORRECT_ANY')";
	public static final String HOLIDAY_MANAGE = "hasAuthority('HOLIDAY_MANAGE')";
	public static final String MARKS_WRITE = "hasAuthority('MARKS_WRITE')";
	public static final String MARKS_READ_SELF = "hasAuthority('MARKS_READ_SELF')";
	public static final String EXAM_MANAGE = "hasAuthority('EXAM_MANAGE')";
	public static final String EXAM_PAPER_UPLOAD = "hasAuthority('EXAM_PAPER_UPLOAD')";
	public static final String EXAM_PAPER_READ = "hasAuthority('EXAM_PAPER_READ')";
	public static final String FEE_MANAGE = "hasAuthority('FEE_MANAGE')";
	public static final String FEE_READ_FULL = "hasAuthority('FEE_READ_FULL')";
	public static final String FEE_READ_STATUS = "hasAuthority('FEE_READ_STATUS')";
	public static final String FEE_PAY_SELF = "hasAuthority('FEE_PAY_SELF')";
	public static final String PAYROLL_MANAGE = "hasAuthority('PAYROLL_MANAGE')";
	public static final String PAYROLL_READ_SELF = "hasAuthority('PAYROLL_READ_SELF')";
	public static final String PAYROLL_READ = "hasAuthority('PAYROLL_READ')";
	public static final String ACCOUNTS_MANAGE = "hasAuthority('ACCOUNTS_MANAGE')";
	public static final String ACCOUNTS_READ = "hasAuthority('ACCOUNTS_READ')";
	public static final String NOTICE_WRITE_ALL = "hasAuthority('NOTICE_WRITE_ALL')";
	public static final String NOTICE_WRITE_CLASS = "hasAuthority('NOTICE_WRITE_CLASS')";
	public static final String NOTICE_READ = "hasAuthority('NOTICE_READ')";
	public static final String QUIZ_MANAGE = "hasAuthority('QUIZ_MANAGE')";
	public static final String QUIZ_MANAGE_ALL = "hasAuthority('QUIZ_MANAGE_ALL')";
	public static final String QUIZ_ATTEMPT = "hasAuthority('QUIZ_ATTEMPT')";
	public static final String ACADEMICS_MANAGE = "hasAuthority('ACADEMICS_MANAGE')";
	public static final String SCHOOL_CONFIG_MANAGE = "hasAuthority('SCHOOL_CONFIG_MANAGE')";

	/** Not a permission: the "any logged-in user" rule the academics read endpoints use. */
	public static final String AUTHENTICATED = "isAuthenticated()";

	/**
	 * The student list, which an admin and a teacher reach with the same query. Only gate-keeps the
	 * endpoint — the rows it returns are the teacher-safe projection either way.
	 */
	public static final String STUDENT_READ_BASIC_OR_FULL =
			"hasAnyAuthority('STUDENT_READ_BASIC', 'STUDENT_READ_FULL')";

	/**
	 * Writing a notice at all (B10). Which audiences are allowed, and whose notices may be edited, is
	 * an object-level rule in {@code NoticeService}: a teacher holding only {@code NOTICE_WRITE_CLASS}
	 * gets through here and is then held to class notices they wrote.
	 */
	public static final String NOTICE_WRITE_ALL_OR_CLASS =
			"hasAnyAuthority('NOTICE_WRITE_ALL', 'NOTICE_WRITE_CLASS')";

	/**
	 * The payroll register and one record's slip, which an accountant reads and an administrator both
	 * reads and writes. Only gate-keeps the endpoint; {@code PayrollService#requireReadable} still
	 * decides whose record a caller may see.
	 */
	public static final String PAYROLL_READ_OR_MANAGE =
			"hasAnyAuthority('PAYROLL_READ', 'PAYROLL_MANAGE')";

	/**
	 * Reading the books (B21). Spelled as either permission because {@code ACCOUNTS_MANAGE} without
	 * {@code ACCOUNTS_READ} should still be able to see what it just wrote, and because an administrator
	 * holds both.
	 */
	public static final String ACCOUNTS_READ_OR_MANAGE =
			"hasAnyAuthority('ACCOUNTS_READ', 'ACCOUNTS_MANAGE')";

	/**
	 * The two accounts endpoints that are an administrator's and not an accountant's: locking a month
	 * and the one-time backfill.
	 *
	 * <p>Written as the two permissions it actually needs rather than as {@code hasRole('ADMIN')},
	 * because CLAUDE.md rule 1 forbids authorising on a role name. {@code SCHOOL_CONFIG_MANAGE} is the
	 * permission that separates an administrator from an accountant — the accountant keeps the books,
	 * the administrator decides what the deployment does — so requiring both says "an accountant may
	 * not close their own month" without inventing a third permission for it.
	 */
	public static final String ACCOUNTS_ADMIN =
			"hasAuthority('ACCOUNTS_MANAGE') and hasAuthority('SCHOOL_CONFIG_MANAGE')";

	public static final String DASHBOARD_ADMIN = "hasAuthority('DASHBOARD_ADMIN')";
	public static final String AUDIT_READ = "hasAuthority('AUDIT_READ')";
	public static final String DEMO_DATA_MANAGE = "hasAuthority('DEMO_DATA_MANAGE')";
	public static final String AI_USE = "hasAuthority('AI_USE')";

	/**
	 * The at-risk scan (B18). It reads attendance, results and fee status for every student on the
	 * roll, which is the admin dashboard's reach rather than a teacher's — so it is spelled as the
	 * two permissions it actually needs, and a teacher holding {@code AI_USE} alone is refused.
	 */
	public static final String AI_INSIGHTS = "hasAuthority('AI_USE') and hasAuthority('DASHBOARD_ADMIN')";

	private HasPermission() {
	}
}

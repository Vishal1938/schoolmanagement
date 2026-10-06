package com.school.people.api;

/**
 * What {@code GET /employees/{uniqueId}} returns, which depends on who is asking: an administrator,
 * or the employee themselves. Anybody else gets 403 rather than a thinner projection — there is no
 * "other people's colleague record" view in this system.
 *
 * <p>Both projections carry the decrypted account number, which is why neither is given to a third
 * party. The list endpoint, which an admin uses to browse, carries only the last four digits.
 */
public sealed interface EmployeeView permits EmployeeAdminView, EmployeeSelfView {

	String uniqueId();
}

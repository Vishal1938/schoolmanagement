package com.school.dashboard.api;

import com.school.common.security.HasPermission;
import com.school.dashboard.app.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The role dashboards: one aggregated read per role, so a landing screen is one request.
 *
 * <p>Not behind the module gate. Every section that belongs to a switchable module is omitted from
 * the body instead — see {@link AdminDashboardResponse} — because a dashboard has to keep working
 * for whatever the institution does run.
 */
@RestController
@RequestMapping("/dashboard")
@Tag(name = "Dashboards", description = "Aggregated landing-screen figures, one endpoint per role")
public class DashboardController {

	private final AdminDashboardService admin;

	public DashboardController(AdminDashboardService admin) {
		this.admin = admin;
	}

	@GetMapping("/admin")
	@PreAuthorize(HasPermission.DASHBOARD_ADMIN)
	@Operation(summary = "The institution at a glance",
			description = "Head counts, today's register and today's takings, where the session's and this "
					+ "month's fees stand class by class, what one-off charges are still owed, unpaid "
					+ "salaries and the last ten receipts. Every amount is in paise. Sections belonging to a "
					+ "module that is switched off — exams, notices, quizzes, studentAttendance — are absent "
					+ "from the body rather than empty; a module that is on but has nothing yet comes back "
					+ "as zeros. On a deployment with no active academic session, session is absent and the "
					+ "fee sections are empty.")
	public AdminDashboardResponse admin() {
		return admin.snapshot();
	}
}

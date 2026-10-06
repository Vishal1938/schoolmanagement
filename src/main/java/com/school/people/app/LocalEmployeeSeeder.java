package com.school.people.app;

import com.school.auth.app.UserRef;
import com.school.auth.app.UserService;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.security.Role;
import com.school.people.domain.EmployeeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Back-fills employee records for the dev logins B2 seeded, so the teacher picker and the employee
 * screens have something in them without anybody hiring a teacher by hand first.
 *
 * <p>Each record is created under the login's own {@code uniqueId}, which is what makes the two
 * halves one identity. Only logins with no employee record are touched, so a restart adds nothing
 * and an edit survives one.
 *
 * <p>The bootstrap admin is skipped on purpose: it holds an EMP id but is an account rather than a
 * person on the payroll, and giving it an employee record would put it on staff lists and in
 * payroll runs.
 */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", matchIfMissing = true)
@Order(LocalEmployeeSeeder.ORDER)
public class LocalEmployeeSeeder implements ApplicationRunner {

	/** After LocalUserSeeder (20), which is where these logins come from. */
	static final int ORDER = 25;

	/** What the seeded staff member does. Local only; a real school types its own. */
	private static final String STAFF_DESIGNATION = "Accountant";

	private static final Logger log = LoggerFactory.getLogger(LocalEmployeeSeeder.class);

	private final UserService users;
	private final EmployeeService employees;
	private final AuditService audit;

	public LocalEmployeeSeeder(UserService users, EmployeeService employees, AuditService audit) {
		this.users = users;
		this.employees = employees;
		this.audit = audit;
	}

	@Override
	public void run(ApplicationArguments args) {
		int created = seed(Role.TEACHER, EmployeeType.TEACHER) + seed(Role.STAFF, EmployeeType.STAFF);
		if (created > 0) {
			audit.record(AuditAction.EMPLOYEES_SEEDED, "Employee", "local",
					"Back-filled " + created + " employee record(s) for existing local logins");
		}
	}

	private int seed(Role role, EmployeeType type) {
		int created = 0;
		for (UserRef user : users.findByRole(role)) {
			if (employees.createForExistingLogin(user.uniqueId(), user.name(), type, STAFF_DESIGNATION)) {
				log.info("Seeded local {} employee record for {}", type, user.uniqueId());
				created++;
			}
		}
		return created;
	}
}

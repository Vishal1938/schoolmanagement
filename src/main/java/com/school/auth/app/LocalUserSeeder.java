package com.school.auth.app;

import com.school.auth.domain.User;
import com.school.common.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Gives local development one user per non-admin role, so the permission model can be exercised before
 * B5 and B6 exist to create real students and employees.
 *
 * <p>{@code @Profile("local")} only — these accounts share one well-known password and must never exist
 * anywhere else. Each role is seeded only when no user holds it yet, so a restart adds nothing.
 */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", matchIfMissing = true)
@Order(LocalUserSeeder.ORDER)
public class LocalUserSeeder implements ApplicationRunner {

	/** After the bootstrap admin, so the admin gets the first EMP number. */
	static final int ORDER = 20;

	/** Documented in TASKS.md B2 and shared by all three seeded accounts. Local profile only. */
	private static final String PASSWORD = "Password@123";

	private static final Logger log = LoggerFactory.getLogger(LocalUserSeeder.class);

	private final UserService users;

	public LocalUserSeeder(UserService users) {
		this.users = users;
	}

	@Override
	public void run(ApplicationArguments args) {
		seed(Role.TEACHER, "Demo Teacher", "teacher@local.test", false);
		// A student's password is always issued by an admin, so the change is forced on first login.
		seed(Role.STUDENT, "Demo Student", "student@local.test", true);
		seed(Role.STAFF, "Demo Staff", "staff@local.test", false);
	}

	private void seed(Role role, String name, String email, boolean mustChangePassword) {
		if (users.existsWithRole(role)) {
			log.debug("A {} account already exists; nothing seeded for that role", role);
			return;
		}
		User user = users.create(new NewUser(role, name, email, PASSWORD, mustChangePassword, null));
		// The password itself stays out of the log, as it does everywhere else; see TASKS.md B2 for it.
		log.info("Seeded local {} account: uniqueId {}{}", role, user.getUniqueId(),
				mustChangePassword ? " (must change password on first login)" : "");
	}
}

package com.school.auth.app;

import com.school.auth.domain.User;
import com.school.common.config.AppProperties;
import com.school.common.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Creates the first ADMIN on first boot from {@code BOOTSTRAP_ADMIN_EMAIL} and
 * {@code BOOTSTRAP_ADMIN_PASSWORD}, and logs the generated EMP uniqueId once so whoever deployed the
 * instance knows what to log in with. There is no self-signup, so without this nobody could ever get in.
 *
 * <p>Runs on every boot but does nothing once an ADMIN exists, and never touches an existing account:
 * changing the bootstrap variables later does not reset a password. The password is used to compute a
 * BCrypt hash and is never logged, returned or audited.
 */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", matchIfMissing = true)
@Order(BootstrapAdminInitializer.ORDER)
public class BootstrapAdminInitializer implements ApplicationRunner {

	/** Before the local seeder, so the admin holds the first EMP number in a fresh deployment. */
	static final int ORDER = 10;

	private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

	private final UserService users;
	private final AppProperties properties;

	public BootstrapAdminInitializer(UserService users, AppProperties properties) {
		this.users = users;
		this.properties = properties;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (users.existsWithRole(Role.ADMIN)) {
			log.debug("An ADMIN account already exists; the bootstrap admin was not recreated");
			return;
		}
		String email = properties.bootstrapAdmin().email();
		String password = properties.bootstrapAdmin().password();
		if (isBlank(email) || isBlank(password)) {
			// Failing here beats starting an instance that nobody can log in to.
			throw new IllegalStateException("There is no ADMIN account and no bootstrap credentials to create one. "
					+ "Set BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD and start again.");
		}
		// mustChangePassword is false: this password was chosen by the deployer, not issued to them.
		User admin = users.create(new NewUser(Role.ADMIN, "Administrator", email, password, false, null));
		log.info("Created the bootstrap ADMIN account. Log in with uniqueId {} and BOOTSTRAP_ADMIN_PASSWORD.",
				admin.getUniqueId());
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}

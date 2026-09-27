package com.school;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Guards the module boundaries from CLAUDE.md: a module may only reach another module through that
 * module's public services, never through its repositories or documents. Every task must keep this
 * test green.
 */
class ModularityTests {

	static final ApplicationModules MODULES = ApplicationModules.of(SchoolManagementApplication.class);

	@Test
	void modulesRespectTheirBoundaries() {
		MODULES.verify();
	}

	@Test
	void everyFeaturePackageIsDetectedAsAModule() {
		// Fails loudly if a module package is renamed or loses its package-info.java.
		for (String module : new String[] {"common", "schoolconfig", "auth", "academics", "people", "attendance",
				"exams", "quiz", "notice", "fees", "payment", "payroll", "dashboard", "ai"}) {
			MODULES.getModuleByName(module)
					.orElseThrow(() -> new AssertionError("Missing application module: " + module));
		}
	}
}

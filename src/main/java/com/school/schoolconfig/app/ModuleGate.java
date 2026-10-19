package com.school.schoolconfig.app;

import com.school.common.config.AppProperties;
import com.school.common.exceptions.AppException;
import com.school.common.exceptions.ErrorType;
import com.school.schoolconfig.domain.Modules;
import com.school.schoolconfig.domain.SchoolModule;
import com.school.schoolconfig.domain.Terms;
import org.springframework.stereotype.Component;

/**
 * The one place that answers "is this module on in this deployment", and the 404 it produces.
 *
 * <p><strong>404, not 403 or 503.</strong> The same reasoning as the AI switch it generalises: a
 * college that does not run quizzes should look like a build that never had them. A 503 would promise
 * they are coming back, and a 403 would invite somebody to ask for access to a route nobody can be
 * given. The {@code code} stays {@code FEATURE_DISABLED} so a client can still tell this apart from a
 * mistyped path.
 *
 * <p>Read live from {@code school_config} rather than cached. The document is a single-key lookup, an
 * administrator can switch a module off and expect it to take effect, and a cache would have to be
 * invalidated from {@code PUT /school/config} — more moving parts than the read costs.
 *
 * <p>{@link SchoolModule#AI} is the one module with a second gate: {@code APP_FEATURES_AI} is the
 * deployment's choice (no key configured, no AI) and the module switch is the institution's, so both
 * have to say yes. That keeps the existing behaviour intact — the environment variable defaults to
 * false, so turning the module on does not conjure AI into a deployment that has no provider key.
 */
@Component
public class ModuleGate {

	private final SchoolConfigService config;
	private final boolean aiAvailable;

	public ModuleGate(SchoolConfigService config, AppProperties properties) {
		this.config = config;
		this.aiAvailable = properties.features().ai();
	}

	/** Whether the module is on. */
	public boolean isEnabled(SchoolModule module) {
		if (module == SchoolModule.AI && !aiAvailable) {
			return false;
		}
		return module.isOn(modules());
	}

	/**
	 * Called at the start of anything behind a module switch.
	 *
	 * @throws AppException 404 {@code FEATURE_DISABLED} when the module is off
	 */
	public void require(SchoolModule module) {
		if (!isEnabled(module)) {
			throw new AppException(ErrorType.FEATURE_DISABLED_NOT_FOUND,
					"This endpoint is not available in this deployment");
		}
	}

	/**
	 * The whole set, as published on {@code GET /public/school}.
	 *
	 * <p>{@code ai} is reported as the <em>effective</em> value — the module switch and the environment
	 * switch together — so the frontend has one truth to read rather than two to combine.
	 */
	public Modules effectiveModules() {
		Modules stored = modules();
		return aiAvailable ? stored : new Modules(stored.exams(), stored.quizzes(), stored.examPapers(),
				stored.notices(), stored.studentAttendance(), stored.studentLogin(), stored.onlinePayments(),
				stored.landingPage(), false);
	}

	/** The institution's vocabulary, defaulted. */
	public Terms terms() {
		return config.get().resolvedTerms();
	}

	private Modules modules() {
		return config.get().resolvedModules();
	}
}

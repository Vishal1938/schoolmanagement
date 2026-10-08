package com.school.ai.app;

import com.school.common.config.AppProperties;
import com.school.common.exceptions.AppException;
import com.school.common.exceptions.ErrorType;
import org.springframework.stereotype.Component;

/**
 * The {@code app.features.ai} switch, and the 404 it produces.
 *
 * <p><strong>404, not 403 or 503.</strong> A school that has not bought into the AI features should
 * look to the outside like a build that never had them: a 503 would say "come back later" about
 * something that is never coming, and a 403 would say "ask for permission" about a route nobody can
 * be given access to. The {@code code} stays {@code FEATURE_DISABLED} so a frontend that does ask can
 * still tell this apart from a mistyped path — see {@code ErrorType.FEATURE_DISABLED_NOT_FOUND}.
 *
 * <p>The frontend is not meant to need this: {@code GET /public/school} carries
 * {@code features.ai}, so the whole section can be hidden before anything is called.
 */
@Component
public class AiFeature {

	private final boolean enabled;

	public AiFeature(AppProperties properties) {
		this.enabled = properties.features().ai();
	}

	public boolean isEnabled() {
		return enabled;
	}

	/** Called first in every AI endpoint, the public chat included. */
	public void require() {
		if (!enabled) {
			throw new AppException(ErrorType.FEATURE_DISABLED_NOT_FOUND,
					"This endpoint is not available in this deployment");
		}
	}
}

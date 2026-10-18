package com.school.schoolconfig.api;

import java.time.Duration;

import com.school.common.config.AppProperties;
import com.school.schoolconfig.app.ModuleGate;
import com.school.schoolconfig.app.SchoolConfigService;
import com.school.schoolconfig.domain.Modules;
import com.school.schoolconfig.domain.SchoolConfig;
import com.school.schoolconfig.domain.SchoolModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The landing page's data source. Open to anyone: {@code /public/**} is permit-all in
 * {@code SecurityConfig}, and {@link SecurityRequirements} opts the operation out of the global
 * bearer requirement in the OpenAPI spec.
 */
@RestController
@RequestMapping("/public/school")
@Tag(name = "Public", description = "Endpoints an anonymous visitor may call")
public class PublicSchoolController {

	private final SchoolConfigService service;
	private final SchoolConfigMapper mapper;
	private final ModuleGate gate;
	private final boolean aiAvailable;

	public PublicSchoolController(SchoolConfigService service, SchoolConfigMapper mapper, ModuleGate gate,
			AppProperties properties) {
		this.service = service;
		this.mapper = mapper;
		this.gate = gate;
		// Fixed for the life of the process: this is an environment switch, not content.
		this.aiAvailable = properties.features().ai();
	}

	@GetMapping
	@SecurityRequirements
	@Operation(summary = "School identity, vocabulary, modules and landing-page content",
			description = "Public-safe fields only: no receipt prefix, no attendance window, "
					+ "no grading scheme, nothing internal. Carries `terms` (what this institution calls a "
					+ "class, a section and a teacher) and `modules` (which features are on, where off means "
					+ "those endpoints answer 404 FEATURE_DISABLED), so the frontend can label and navigate "
					+ "before it calls anything. With the landingPage module off, everything from `about` "
					+ "downwards is omitted and the identity, terms and modules remain.")
	public ResponseEntity<PublicSchoolResponse> get() {
		SchoolConfig config = service.get();
		// Nulling the landing block is the whole of the landingPage switch: every field the mapper takes
		// from it reads through a null-safe path, so they all drop out of the response together.
		SchoolConfig visible = gate.isEnabled(SchoolModule.LANDING_PAGE) ? config : config.toBuilder()
				.landing(null)
				.build();
		Modules modules = gate.effectiveModules();
		return ResponseEntity.ok()
				// Content changes rarely and this is the most-hit endpoint in the deployment.
				.cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
				.body(mapper.toPublicResponse(visible, new PublicSchoolResponse.Features(aiAvailable && modules.ai()),
						modules));
	}
}

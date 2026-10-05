package com.school.schoolconfig.api;

import java.time.Duration;

import com.school.schoolconfig.app.SchoolConfigService;
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

	public PublicSchoolController(SchoolConfigService service, SchoolConfigMapper mapper) {
		this.service = service;
		this.mapper = mapper;
	}

	@GetMapping
	@SecurityRequirements
	@Operation(summary = "School identity and landing-page content",
			description = "Public-safe fields only: no receipt prefix, no attendance window, "
					+ "no grading scheme, nothing internal.")
	public ResponseEntity<PublicSchoolResponse> get() {
		return ResponseEntity.ok()
				// Content changes rarely and this is the most-hit endpoint in the deployment.
				.cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
				.body(mapper.toPublicResponse(service.get()));
	}
}

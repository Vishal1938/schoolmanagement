package com.school.common.config;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc setup. The generated spec is the artefact the frontend repo consumes, so it must stay in
 * step with API_CONTRACT.md.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	public static final String BEARER_SCHEME = "bearerAuth";

	private final AppProperties properties;

	public OpenApiConfig(AppProperties properties) {
		this.properties = properties;
	}

	@Bean
	public OpenAPI schoolManagementOpenApi() {
		OpenAPI openApi = new OpenAPI()
				.info(new Info()
						.title("School Management API")
						.version("v1")
						.description("Backend for a single school deployment. Roles: ADMIN, TEACHER, STUDENT, STAFF. "
								+ "All money values are long paise.")
						.license(new License().name("Proprietary")))
				.components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")
						.description("Access token from POST /auth/login")))
				// Applied globally; endpoints under /public are annotated to opt out.
				.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));

		// The origin only — never with app.api-base-path on it. Every path in this document already
		// starts with that prefix, so a server URL carrying it as well would have generated clients
		// calling /api/v1/api/v1/...; AppProperties#resolvedPublicBaseUrl is where that is enforced.
		String publicBaseUrl = properties.resolvedPublicBaseUrl();
		if (publicBaseUrl != null) {
			openApi.setServers(List.of(new Server()
					.url(publicBaseUrl)
					.description("This deployment")));
		}
		// Left unset otherwise, and springdoc then derives the server from the incoming request. That
		// is right for local development and a trap behind a proxy: a host reachable over both http
		// and https answers with whichever scheme the caller happened to use, and Swagger UI loaded
		// over https cannot call an http server — the request is cross-origin, CORS is disabled here
		// because nothing we serve is meant to be, and the browser reports it as a CORS failure.
		// Worse, springdoc caches the document, so the first caller after a restart decides it for
		// everybody. Set APP_PUBLIC_BASE_URL on any real deployment.
		return openApi;
	}
}

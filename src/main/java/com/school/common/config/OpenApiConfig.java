package com.school.common.config;

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

	@Bean
	public OpenAPI schoolManagementOpenApi(AppProperties properties) {
		return new OpenAPI()
				.info(new Info()
						.title("School Management API")
						.version("v1")
						.description("Backend for a single school deployment. Roles: ADMIN, TEACHER, STUDENT, STAFF. "
								+ "All money values are long paise.")
						.license(new License().name("Proprietary")))
				.addServersItem(new Server().url(properties.apiBasePath()).description("Current deployment"))
				.components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")
						.description("Access token from POST /auth/login")))
				// Applied globally; endpoints under /public are annotated to opt out.
				.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
	}
}

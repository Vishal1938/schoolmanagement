package com.school.common;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Context-level checks that do not need a database. The Mongo health indicator is switched off here;
 * {@code MongoReplicaSetIT} covers health against a real replica set.
 */
@SpringBootTest(properties = "management.health.mongo.enabled=false")
@AutoConfigureMockMvc
class ApplicationStartupTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	Clock clock;

	@Test
	void healthEndpointIsPublicAndReportsUp() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void openApiSpecIsServedAndDocumentsTheBearerScheme() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("School Management API"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
	}

	@Test
	void unauthenticatedCallToAProtectedPathReturnsProblemDetail() throws Exception {
		mockMvc.perform(get("/api/v1/students"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.type").value("https://schoolmanagement.dev/problems/unauthorized"))
				.andExpect(jsonPath("$.instance").value("/api/v1/students"))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void clockBeanIsConfiguredForTheSchoolTimezone() {
		assertThat(clock.getZone().getId()).isEqualTo("Asia/Kolkata");
	}
}

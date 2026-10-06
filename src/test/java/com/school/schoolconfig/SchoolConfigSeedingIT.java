package com.school.schoolconfig;

import com.school.schoolconfig.domain.SchoolConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B1's definition of done, end to end: an empty database is seeded on startup and the landing page
 * can be read by an anonymous visitor.
 *
 * <p>Requires a running Docker daemon; bound to the {@code verify} phase, not {@code test}.
 */
// Seeding stays ON here — it is the subject of the test. That also runs B2's
// BootstrapAdminInitializer, which against a fresh container finds no ADMIN and then refuses to start
// unless it has credentials to create one, so throwaway ones are supplied. They are deliberately
// unusable outside this test: .invalid is the reserved TLD for exactly this (RFC 2606).
@SpringBootTest(properties = {
		"app.school-code=ITS",
		"app.bootstrap-admin.email=integration-test@school.invalid",
		"app.bootstrap-admin.password=integration-test-only-never-a-real-password"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SchoolConfigSeedingIT {

	@Container
	@ServiceConnection
	static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	MongoTemplate mongoTemplate;

	@Test
	void aFreshDatabaseIsSeededWithExactlyOneConfiguration() {
		assertThat(mongoTemplate.count(new Query(), SchoolConfig.COLLECTION)).isEqualTo(1);

		SchoolConfig stored = mongoTemplate.findById(SchoolConfig.SINGLETON_ID, SchoolConfig.class);
		assertThat(stored).isNotNull();
		assertThat(stored.getCode()).isEqualTo("ITS");
		assertThat(stored.getIdentity().name()).isNotBlank();
		assertThat(stored.getAcademicSettings().receiptPrefix()).isNotBlank();
		assertThat(stored.getGradingScheme().bands()).isNotEmpty();
		assertThat(stored.getCreatedAt()).isNotNull();
	}

	@Test
	void theLandingPageIsReadableWithoutAToken() throws Exception {
		mockMvc.perform(get("/api/v1/public/school"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").isNotEmpty())
				.andExpect(jsonPath("$.about").isNotEmpty())
				.andExpect(jsonPath("$.facilities").isArray())
				.andExpect(jsonPath("$.stats.students").isNumber())
				.andExpect(jsonPath("$.contact.email").isNotEmpty())
				// The internals stay out of the public projection even with a real document behind it.
				.andExpect(jsonPath("$.academicSettings").doesNotExist())
				.andExpect(jsonPath("$.gradingScheme").doesNotExist())
				.andExpect(jsonPath("$.code").doesNotExist());
	}

	@Test
	void theConfigurationEndpointStillRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/school/config"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}
}

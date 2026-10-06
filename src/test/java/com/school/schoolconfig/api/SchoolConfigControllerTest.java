package com.school.schoolconfig.api;

import java.time.Instant;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.common.audit.AuditService;
import com.school.schoolconfig.SchoolConfigFixtures;
import com.school.schoolconfig.domain.SchoolConfig;
import com.school.schoolconfig.infra.SchoolConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin endpoints against the real security filter chain, so the permission check, the problem
 * details and Bean Validation are all the production ones. Mongo and S3 are the only fakes.
 */
@SpringBootTest(properties = {
		"app.seed.enabled=false",
		"management.health.mongo.enabled=false",
		"spring.data.mongodb.auto-index-creation=false"
})
@AutoConfigureMockMvc
class SchoolConfigControllerTest {

	private static final String MANAGE = "SCHOOL_CONFIG_MANAGE";
	private static final Instant NOW = Instant.parse("2026-04-01T09:30:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@MockitoBean
	SchoolConfigRepository repository;

	@MockitoBean
	S3Client s3Client;

	/** Mocked so a config update in these tests does not try to write an audit entry to a real Mongo. */
	@MockitoBean
	AuditService auditService;

	@BeforeEach
	void configurationExists() {
		when(repository.findById(SchoolConfig.SINGLETON_ID))
				.thenReturn(Optional.of(SchoolConfigFixtures.seeded("TPS", NOW)));
		when(repository.save(any(SchoolConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	// --- read ------------------------------------------------------------------------------------

	@Test
	@WithAnonymousUser
	void readingTheConfigurationNeedsAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/school/config"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	@WithMockUser(authorities = "STUDENT_READ_BASIC")
	void anotherPermissionIsNotEnough() throws Exception {
		mockMvc.perform(get("/api/v1/school/config"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"))
				.andExpect(jsonPath("$.type").value("https://schoolmanagement.dev/problems/forbidden"));
	}

	@Test
	@WithMockUser(authorities = MANAGE)
	void theAdminViewCarriesTheInternalSettingsToo() throws Exception {
		mockMvc.perform(get("/api/v1/school/config"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("TPS"))
				.andExpect(jsonPath("$.identity.name").value("Test Public School"))
				.andExpect(jsonPath("$.academicSettings.receiptPrefix").value("TPS-RCP"))
				.andExpect(jsonPath("$.academicSettings.attendanceEditWindowHours").value(48))
				.andExpect(jsonPath("$.gradingScheme.mode").value("BOTH"))
				.andExpect(jsonPath("$.updatedAt").exists());
	}

	/** The landing page must stay reachable without a token; /public/** is permit-all. */
	@Test
	@WithAnonymousUser
	void thePublicEndpointIsOpen() throws Exception {
		mockMvc.perform(get("/api/v1/public/school"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Test Public School"))
				.andExpect(jsonPath("$.academicSettings").doesNotExist());
	}

	// --- write -----------------------------------------------------------------------------------

	@Test
	@WithMockUser(authorities = MANAGE)
	void updatingReplacesTheEditableContent() throws Exception {
		SchoolConfigRequest request = SchoolConfigFixtures.validRequest();

		mockMvc.perform(put("/api/v1/school/config")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.identity.name").value("Test Public School"))
				.andExpect(jsonPath("$.code").value("TPS"));
	}

	@Test
	@WithMockUser(authorities = "STUDENT_READ_BASIC")
	void updatingWithoutThePermissionIsForbidden() throws Exception {
		mockMvc.perform(put("/api/v1/school/config")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(SchoolConfigFixtures.validRequest())))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(authorities = MANAGE)
	void invalidContentIsReportedFieldByField() throws Exception {
		String body = objectMapper.writeValueAsString(SchoolConfigFixtures.validRequest())
				.replace("\"#0B3D91\"", "\"navy\"")
				.replace("\"Test Public School\"", "\"\"")
				.replace("\"TPS-RCP\"", "\"tps rcp\"");

		mockMvc.perform(put("/api/v1/school/config").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[?(@.field == 'identity.name')]").exists())
				.andExpect(jsonPath("$.errors[?(@.field == 'identity.theme.primary')]").exists())
				.andExpect(jsonPath("$.errors[?(@.field == 'academicSettings.receiptPrefix')]").exists());
	}

	@Test
	@WithMockUser(authorities = MANAGE)
	void aGradingSchemeWithAGapIsRejectedWithFieldErrors() throws Exception {
		String body = objectMapper.writeValueAsString(SchoolConfigFixtures.validRequest())
				.replace("\"minPercentage\":91", "\"minPercentage\":95");

		mockMvc.perform(put("/api/v1/school/config").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[?(@.field == 'gradingScheme.bands')]").exists());
	}

	@Test
	@WithMockUser(authorities = MANAGE)
	void aConcurrentSaveIsAConflictRatherThanAServerError() throws Exception {
		when(repository.save(any(SchoolConfig.class)))
				.thenThrow(new OptimisticLockingFailureException("version 0 no longer current"));

		mockMvc.perform(put("/api/v1/school/config")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(SchoolConfigFixtures.validRequest())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONFLICT"));
	}

	// --- image upload ----------------------------------------------------------------------------

	@Test
	@WithMockUser(authorities = MANAGE)
	void uploadingALogoReturnsItsPublicUrl() throws Exception {
		byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01, 0x02, 0x03};
		MockMultipartFile file = new MockMultipartFile("file", "logo.png", MediaType.IMAGE_PNG_VALUE, png);

		mockMvc.perform(multipart("/api/v1/school/config/images").file(file).param("category", "LOGO"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.matchesPattern(
						"http://localhost:9000/school-media/public/logo/[0-9a-f-]{36}\\.png")));
	}

	@Test
	@WithMockUser(authorities = MANAGE)
	void aFileThatIsNotAnImageIsRejectedWhateverItClaimsToBe() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "logo.png", MediaType.IMAGE_PNG_VALUE,
				"<svg onload=\"alert(1)\"/>".getBytes());

		mockMvc.perform(multipart("/api/v1/school/config/images").file(file).param("category", "LOGO"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[?(@.field == 'file')]").exists());
	}

	// --- spec ------------------------------------------------------------------------------------

	/**
	 * The frontend generates its client from this spec, so the paths have to be the ones it will call —
	 * prefix included, and with no {@code servers} entry repeating the prefix.
	 */
	@Test
	@WithAnonymousUser
	void theSpecDescribesTheEndpointsAtTheirRealPaths() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				// springdoc fills in the origin itself; it must not also carry the /api/v1 prefix.
				.andExpect(jsonPath("$.servers[0].url").value(
						org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/api/v1"))))
				.andExpect(jsonPath("$.paths['/api/v1/public/school'].get").exists())
				.andExpect(jsonPath("$.paths['/api/v1/school/config'].get").exists())
				.andExpect(jsonPath("$.paths['/api/v1/school/config'].put").exists())
				.andExpect(jsonPath("$.paths['/api/v1/school/config/images'].post").exists())
				// The landing page opts out of the global bearer requirement.
				.andExpect(jsonPath("$.paths['/api/v1/public/school'].get.security").isEmpty());
	}

	@Test
	@WithAnonymousUser
	void uploadingNeedsAuthentication() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "logo.png", MediaType.IMAGE_PNG_VALUE, new byte[] {1});

		mockMvc.perform(multipart("/api/v1/school/config/images").file(file).param("category", "LOGO"))
				.andExpect(status().isUnauthorized());
	}
}

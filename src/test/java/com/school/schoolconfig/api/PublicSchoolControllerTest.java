package com.school.schoolconfig.api;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.common.exceptions.GlobalExceptionHandler;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ProblemDetailFactory;
import com.school.schoolconfig.SchoolConfigFixtures;
import com.school.schoolconfig.app.SchoolConfigService;
import com.school.schoolconfig.domain.Landing;
import com.school.schoolconfig.domain.Principal;
import com.school.schoolconfig.domain.SchoolConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The landing-page endpoint, and above all what it must never contain. */
class PublicSchoolControllerTest {

	private static final Instant NOW = Instant.parse("2026-04-01T09:30:00Z");

	private SchoolConfigService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = mock(SchoolConfigService.class);
		// Mirrors spring.jackson.default-property-inclusion=non_null from application.yml, so what these
		// tests see on the wire is what a browser sees: missing optional content is an absent key.
		ObjectMapper objectMapper = new ObjectMapper()
				.setSerializationInclusion(JsonInclude.Include.NON_NULL)
				// Boot's auto-configured mapper adds this; without it a ProblemDetail's code and timestamp
				// would serialise nested under "properties" instead of at the top level.
				.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new PublicSchoolController(service, SchoolConfigFixtures.MAPPER))
				.setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
				.setControllerAdvice(new GlobalExceptionHandler(
						new ProblemDetailFactory(Clock.fixed(NOW, ZoneId.of("Asia/Kolkata")))))
				.build();
	}

	@Test
	void returnsTheLandingPageContent() throws Exception {
		when(service.get()).thenReturn(SchoolConfigFixtures.seeded("TPS", NOW));

		mockMvc.perform(get("/public/school"))
				.andExpect(status().isOk())
				.andExpect(content -> assertThat(content.getResponse().getContentType())
						.startsWith(MediaType.APPLICATION_JSON_VALUE))
				.andExpect(jsonPath("$.name").value("Test Public School"))
				.andExpect(jsonPath("$.tagline").value("Learn and lead"))
				.andExpect(jsonPath("$.theme.primary").value("#0B3D91"))
				.andExpect(jsonPath("$.about").isNotEmpty())
				.andExpect(jsonPath("$.vision").value("Clear thinking, honest speech."))
				.andExpect(jsonPath("$.principal.name").value("Dr. Anita Deshpande"))
				.andExpect(jsonPath("$.principal.designation").value("Principal"))
				.andExpect(jsonPath("$.principal.photoUrl").value("https://media.test/public/gallery/principal.jpg"))
				.andExpect(jsonPath("$.principal.message").value("Visit us on any working day."))
				.andExpect(jsonPath("$.academics.board").value("State board"))
				.andExpect(jsonPath("$.academics.summary").isNotEmpty())
				.andExpect(jsonPath("$.academics.levels[0].name").value("Primary"))
				.andExpect(jsonPath("$.academics.levels[0].range").value("Classes I to V"))
				.andExpect(jsonPath("$.academics.levels[0].description").isNotEmpty())
				.andExpect(jsonPath("$.highlights[0].title").value("Small classes"))
				.andExpect(jsonPath("$.highlights[0].description").value("Thirty-five to a room"))
				.andExpect(jsonPath("$.highlights[0].icon").value("users"))
				.andExpect(jsonPath("$.facilities").isArray())
				.andExpect(jsonPath("$.galleryImageUrls[0]").value("https://media.test/public/gallery/campus.jpg"))
				.andExpect(jsonPath("$.stats.students").value(1240))
				.andExpect(jsonPath("$.contact.email").value("office@test-school.example"))
				.andExpect(jsonPath("$.socialLinks.facebook").value("https://facebook.com/test"))
				.andExpect(jsonPath("$.mapEmbedUrl").value("https://www.google.com/maps/embed?pb=test"));
	}

	/**
	 * The guard that matters: anything not on the allowlist is a leak, so the serialised key set is
	 * compared exactly. Adding a field to the configuration cannot quietly publish it.
	 */
	@Test
	void exposesNothingBeyondTheAllowlist() throws Exception {
		when(service.get()).thenReturn(SchoolConfigFixtures.seeded("TPS", NOW));

		MvcResult result = mockMvc.perform(get("/public/school")).andExpect(status().isOk()).andReturn();
		Map<String, Object> body = new ObjectMapper().readValue(result.getResponse().getContentAsByteArray(),
				new TypeReference<>() { });

		assertThat(body.keySet()).containsExactlyInAnyOrderElementsOf(PublicSchoolResponse.FIELDS);
	}

	@Test
	void hidesTheInternalSettingsByName() throws Exception {
		when(service.get()).thenReturn(SchoolConfigFixtures.seeded("TPS", NOW));

		mockMvc.perform(get("/public/school"))
				.andExpect(status().isOk())
				// Named one by one, so a regression is unmistakable in the failure message.
				.andExpect(jsonPath("$.receiptPrefix").doesNotExist())
				.andExpect(jsonPath("$.academicSettings").doesNotExist())
				.andExpect(jsonPath("$.attendanceEditWindowHours").doesNotExist())
				.andExpect(jsonPath("$.workingDays").doesNotExist())
				.andExpect(jsonPath("$.receiptFooter").doesNotExist())
				.andExpect(jsonPath("$.salarySlipFooter").doesNotExist())
				.andExpect(jsonPath("$.gradingScheme").doesNotExist())
				.andExpect(jsonPath("$.code").doesNotExist())
				.andExpect(jsonPath("$.id").doesNotExist())
				.andExpect(jsonPath("$.version").doesNotExist())
				.andExpect(jsonPath("$.createdAt").doesNotExist())
				.andExpect(jsonPath("$.updatedAt").doesNotExist());
	}

	/**
	 * Every optional field is nullable, so a school that has filled in only the essentials still gets a
	 * 200 with the keys it does have. The absent ones are omitted, not sent as null.
	 */
	@Test
	void stillAnswersWhenAllTheOptionalContentIsMissing() throws Exception {
		when(service.get()).thenReturn(SchoolConfigFixtures.seededMinimal("TPS", NOW));

		MvcResult result = mockMvc.perform(get("/public/school"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Test Public School"))
				.andExpect(jsonPath("$.about").isNotEmpty())
				.andExpect(jsonPath("$.theme.primary").value("#0B3D91"))
				.andExpect(jsonPath("$.stats.students").value(1240))
				.andExpect(jsonPath("$.contact.email").value("office@test-school.example"))
				.andExpect(jsonPath("$.tagline").doesNotExist())
				.andExpect(jsonPath("$.logoUrl").doesNotExist())
				.andExpect(jsonPath("$.faviconUrl").doesNotExist())
				.andExpect(jsonPath("$.vision").doesNotExist())
				.andExpect(jsonPath("$.principal").doesNotExist())
				.andExpect(jsonPath("$.academics").doesNotExist())
				.andExpect(jsonPath("$.highlights").doesNotExist())
				.andExpect(jsonPath("$.facilities").doesNotExist())
				.andExpect(jsonPath("$.galleryImageUrls").doesNotExist())
				.andExpect(jsonPath("$.mapEmbedUrl").doesNotExist())
				.andExpect(jsonPath("$.socialLinks").doesNotExist())
				.andReturn();

		Map<String, Object> body = new ObjectMapper().readValue(result.getResponse().getContentAsByteArray(),
				new TypeReference<>() { });
		assertThat(body.keySet()).isSubsetOf(PublicSchoolResponse.FIELDS);
	}

	/** A principal with only a message, which is how most schools will start out. */
	@Test
	void serialisesAPartiallyFilledPrincipalSection() throws Exception {
		SchoolConfig config = SchoolConfigFixtures.seededMinimal("TPS", NOW);
		SchoolConfig withPrincipal = config.toBuilder()
				.landing(new Landing(config.getLanding().about(), null,
						new Principal(null, null, null, "We are glad you are here."),
						null, List.of(), null, null,
						config.getLanding().stats(), config.getLanding().contact(), null, null))
				.build();
		when(service.get()).thenReturn(withPrincipal);

		mockMvc.perform(get("/public/school"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.principal.message").value("We are glad you are here."))
				.andExpect(jsonPath("$.principal.name").doesNotExist())
				.andExpect(jsonPath("$.principal.designation").doesNotExist())
				.andExpect(jsonPath("$.principal.photoUrl").doesNotExist())
				.andExpect(jsonPath("$.highlights").isEmpty())
				.andExpect(jsonPath("$.academics").doesNotExist());
	}

	@Test
	void isCacheableBecauseTheLandingPageIsTheBusiestEndpoint() throws Exception {
		when(service.get()).thenReturn(SchoolConfigFixtures.seeded("TPS", NOW));

		mockMvc.perform(get("/public/school"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "max-age=300, public"));
	}

	@Test
	void reportsAnUnseededDeploymentAsAProblemDetail() throws Exception {
		when(service.get()).thenThrow(new NotFoundException("The school configuration has not been seeded"));

		mockMvc.perform(get("/public/school"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.type").value("https://schoolmanagement.dev/problems/not-found"))
				.andExpect(jsonPath("$.timestamp").value(NOW.toString()));
	}
}

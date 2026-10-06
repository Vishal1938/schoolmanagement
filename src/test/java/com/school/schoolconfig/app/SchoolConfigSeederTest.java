package com.school.schoolconfig.app;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.common.config.AppProperties;
import com.school.schoolconfig.api.SchoolConfigRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers first-boot seeding, including the sample seed that ships in the jar: if
 * {@code seed/school-seed.json} stops satisfying the API's own constraints, this fails.
 */
class SchoolConfigSeederTest {

	private static ValidatorFactory validatorFactory;
	private static Validator validator;

	@BeforeAll
	static void startValidator() {
		validatorFactory = Validation.buildDefaultValidatorFactory();
		validator = validatorFactory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		validatorFactory.close();
	}

	@Test
	void theSeedThatShipsWithTheJarIsValidAndIsLoadedOnAnEmptyDatabase() throws Exception {
		SchoolConfigService service = mock(SchoolConfigService.class);
		when(service.isSeeded()).thenReturn(false);
		when(service.seedIfMissing(any())).thenReturn(true);

		seeder(service, "classpath:seed/school-seed.json").run(null);

		ArgumentCaptor<SchoolConfigRequest> captor = ArgumentCaptor.forClass(SchoolConfigRequest.class);
		verify(service).seedIfMissing(captor.capture());
		SchoolConfigRequest seed = captor.getValue();
		assertThat(seed.identity().name()).isNotBlank();
		assertThat(seed.identity().theme().primary()).startsWith("#");
		assertThat(seed.landing().facilities()).isNotEmpty();
		assertThat(seed.landing().stats().passPercentage()).isBetween(0, 100);
		// The optional sections are optional in the schema but filled in by the sample.
		assertThat(seed.landing().vision()).isNotBlank();
		assertThat(seed.landing().principal().name()).isNotBlank();
		assertThat(seed.landing().principal().message()).isNotBlank();
		assertThat(seed.landing().academics().board()).isNotBlank();
		assertThat(seed.landing().academics().levels()).isNotEmpty()
				.allSatisfy(level -> assertThat(level.name()).isNotBlank());
		assertThat(seed.landing().highlights()).isNotEmpty()
				.allSatisfy(highlight -> {
					assertThat(highlight.title()).isNotBlank();
					assertThat(highlight.icon()).isNotBlank();
				});
		assertThat(seed.gradingScheme().bands()).isNotEmpty();
		assertThat(seed.academicSettings().receiptPrefix()).isNotBlank();
		assertThat(seed.academicSettings().workingDays()).isNotEmpty();
	}

	@Test
	void anAlreadySeededDeploymentIsLeftAlone() throws Exception {
		SchoolConfigService service = mock(SchoolConfigService.class);
		when(service.isSeeded()).thenReturn(true);

		seeder(service, "classpath:seed/school-seed.json").run(null);

		verify(service, never()).seedIfMissing(any());
	}

	@Test
	void aMissingSeedFileFailsTheBootAndSaysWhere() {
		SchoolConfigService service = mock(SchoolConfigService.class);
		when(service.isSeeded()).thenReturn(false);

		assertThatThrownBy(() -> seeder(service, "classpath:seed/not-there.json").run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("no seed file at classpath:seed/not-there.json");
	}

	@Test
	void anInvalidSeedFailsTheBootAndNamesTheField(@TempDir Path dir) throws Exception {
		SchoolConfigService service = mock(SchoolConfigService.class);
		when(service.isSeeded()).thenReturn(false);
		Path file = dir.resolve("bad-seed.json");
		Files.writeString(file, """
				{
				  "identity": {
				    "name": "",
				    "theme": { "primary": "navy", "secondary": "#F2A93B", "accent": "#12B886" }
				  },
				  "landing": {
				    "about": "About us.",
				    "stats": { "students": 10, "teachers": 2, "years": 1, "passPercentage": 100 },
				    "contact": {
				      "addressLine1": "1 Road", "city": "Demo City",
				      "phone": "+91 755 400 1200", "email": "office@test-school.example"
				    }
				  },
				  "gradingScheme": { "mode": "BOTH", "bands": [
				    { "grade": "A", "minPercentage": 0, "maxPercentage": 100 }
				  ] },
				  "academicSettings": {
				    "workingDays": ["MONDAY"], "attendanceEditWindowHours": 24, "receiptPrefix": "RCP"
				  }
				}
				""");

		assertThatThrownBy(() -> seeder(service, "file:" + file).run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("is invalid")
				.hasMessageContaining("identity.name")
				.hasMessageContaining("identity.theme.primary");
		verify(service, never()).seedIfMissing(any());
	}

	@Test
	void aMisspelledKeyIsNotSilentlyIgnored(@TempDir Path dir) throws Exception {
		SchoolConfigService service = mock(SchoolConfigService.class);
		when(service.isSeeded()).thenReturn(false);
		Path file = dir.resolve("typo-seed.json");
		Files.writeString(file, "{ \"identtiy\": { \"name\": \"Test Public School\" } }");

		assertThatThrownBy(() -> seeder(service, "file:" + file).run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("could not be read");
	}

	private SchoolConfigSeeder seeder(SchoolConfigService service, String location) {
		AppProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
						"app.school-code", "TPS",
						"app.seed.school-config-location", location)))
				.bind("app", AppProperties.class)
				.get();
		return new SchoolConfigSeeder(service, new DefaultResourceLoader(), new ObjectMapper(), validator, properties);
	}
}

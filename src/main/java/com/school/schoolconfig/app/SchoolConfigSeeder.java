package com.school.schoolconfig.app;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.common.config.AppProperties;
import com.school.schoolconfig.api.SchoolConfigRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Seeds {@code school_config} on first boot from {@code app.seed.school-config-location}, so a new
 * deployment needs a JSON file and environment variables rather than a code change.
 *
 * <p>The seed file has the same shape as the {@code PUT /school/config} body and is validated with
 * the same constraints, which is why this class in {@code app} uses the DTO from {@code api}: one
 * validated write shape, whether the content arrives over HTTP or from disk.
 *
 * <p>Startup fails if the file is missing or invalid. That is deliberate — an application whose
 * branding, grading scheme and receipt prefix are unset cannot serve a single meaningful request, and
 * discovering that at boot beats discovering it from a 404 on the landing page.
 */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", matchIfMissing = true)
public class SchoolConfigSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SchoolConfigSeeder.class);

	private final SchoolConfigService service;
	private final ResourceLoader resourceLoader;
	private final ObjectMapper objectMapper;
	private final Validator validator;
	private final AppProperties properties;

	public SchoolConfigSeeder(SchoolConfigService service, ResourceLoader resourceLoader, ObjectMapper objectMapper,
			Validator validator, AppProperties properties) {
		this.service = service;
		this.resourceLoader = resourceLoader;
		// A typo in a seed key must not be ignored, so this reader is stricter than the web one.
		this.objectMapper = objectMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		this.validator = validator;
		this.properties = properties;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (service.isSeeded()) {
			log.debug("School configuration already present; skipping the seed");
			return;
		}
		String location = properties.seed().schoolConfigLocation();
		SchoolConfigRequest seed = read(location);
		validate(seed, location);
		if (service.seedIfMissing(seed)) {
			log.info("Seeded school configuration for {} from {}", properties.schoolCode(), location);
		}
		else {
			log.info("School configuration was seeded by another instance; left untouched");
		}
	}

	private SchoolConfigRequest read(String location) {
		Resource resource = resourceLoader.getResource(location);
		if (!resource.exists()) {
			throw new IllegalStateException("No school configuration in the database and no seed file at " + location
					+ ". Point app.seed.school-config-location at a readable file.");
		}
		try (InputStream in = resource.getInputStream()) {
			return objectMapper.readValue(in, SchoolConfigRequest.class);
		}
		catch (IOException ex) {
			throw new IllegalStateException("The school seed at " + location + " could not be read: "
					+ ex.getMessage(), ex);
		}
	}

	private void validate(SchoolConfigRequest seed, String location) {
		Set<ConstraintViolation<SchoolConfigRequest>> violations = validator.validate(seed);
		if (!violations.isEmpty()) {
			String detail = violations.stream()
					.map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
					.sorted(Comparator.naturalOrder())
					.collect(Collectors.joining("; "));
			throw new IllegalStateException("The school seed at " + location + " is invalid: " + detail);
		}
	}
}

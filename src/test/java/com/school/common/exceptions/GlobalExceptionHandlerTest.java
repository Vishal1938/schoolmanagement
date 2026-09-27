package com.school.common.exceptions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies that every error leaves the application in the one problem-detail shape. */
class GlobalExceptionHandlerTest {

	private static final Instant NOW = Instant.parse("2026-04-01T09:30:00Z");

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		ProblemDetailFactory problems = new ProblemDetailFactory(Clock.fixed(NOW, ZoneId.of("Asia/Kolkata")));
		mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
				.setControllerAdvice(new GlobalExceptionHandler(problems))
				.build();
	}

	@Test
	void beanValidationFailuresAreReportedFieldByField() throws Exception {
		mockMvc.perform(post("/test/students")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"\",\"rollNumber\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.type").value("https://schoolmanagement.dev/problems/validation-error"))
				.andExpect(jsonPath("$.timestamp").value(NOW.toString()))
				.andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
				.andExpect(jsonPath("$.errors[?(@.field == 'rollNumber')]").exists());
	}

	@Test
	void malformedBodyIsRejectedWithoutEchoingThePayload() throws Exception {
		mockMvc.perform(post("/test/students").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_REQUEST"))
				.andExpect(jsonPath("$.detail").value("Request body is missing or malformed"));
	}

	@Test
	void notFoundCarriesTheEntityInTheDetail() throws Exception {
		mockMvc.perform(get("/test/missing"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("Student STU-2026-0001 not found"))
				.andExpect(jsonPath("$.instance").value("/test/missing"));
	}

	@Test
	void bulkValidationErrorsAreReportedTogether() throws Exception {
		mockMvc.perform(get("/test/import"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors.length()").value(2))
				.andExpect(jsonPath("$.errors[0].field").value("row.2.dob"));
	}

	@Test
	void businessRuleFailuresAreUnprocessable() throws Exception {
		mockMvc.perform(get("/test/rule"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("UNPROCESSABLE"))
				.andExpect(jsonPath("$.detail").value("Marks 105 exceed the maximum of 100"));
	}

	@Test
	void unexpectedFailuresReturnAnErrorIdAndNoInternalDetail() throws Exception {
		mockMvc.perform(get("/test/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.errorId").exists())
				.andExpect(jsonPath("$.detail").value("Something went wrong. Quote the errorId when reporting this."));
	}

	@RestController
	@RequestMapping("/test")
	static class TestController {

		@PostMapping("/students")
		void create(@Valid @RequestBody CreateRequest request) {
		}

		@org.springframework.web.bind.annotation.GetMapping("/missing")
		void missing() {
			throw NotFoundException.of("Student", "STU-2026-0001");
		}

		@org.springframework.web.bind.annotation.GetMapping("/import")
		void importSheet() {
			throw new ValidationException("2 rows are invalid", List.of(
					new FieldViolation("row.2.dob", "must be a past date"),
					new FieldViolation("row.7.guardianPhone", "must be 10 digits")));
		}

		@org.springframework.web.bind.annotation.GetMapping("/rule")
		void rule() {
			throw new BusinessRuleException("Marks 105 exceed the maximum of 100");
		}

		@org.springframework.web.bind.annotation.GetMapping("/boom")
		void boom() {
			throw new IllegalStateException("connection pool exhausted at 10.0.0.4:27017");
		}
	}

	record CreateRequest(@NotBlank String name, @Min(1) int rollNumber) {
	}
}

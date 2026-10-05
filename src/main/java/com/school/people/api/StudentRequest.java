package com.school.people.api;

import java.time.LocalDate;

import com.school.people.domain.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /students} and {@code PUT /students/{uniqueId}}.
 *
 * <p>Three things a client might expect are deliberately absent. {@code uniqueId} is issued by the
 * server and never changes. {@code status} moves through {@code PATCH /students/{uniqueId}/status},
 * so an edit to a phone number cannot quietly re-admit somebody who left. And
 * {@code enrollment.sessionId} is always the active session — taking it from the client would let a
 * request file this year's admission under last year.
 */
public record StudentRequest(
		@NotBlank @Size(max = 120) String name,
		@NotNull @Past LocalDate dob,
		@NotNull Gender gender,
		@Size(max = 512) String photoUrl,
		@NotBlank @Size(max = 40) String admissionNo,
		@NotNull LocalDate admissionDate,
		@Valid @NotNull Enrollment enrollment,
		@Valid @NotNull Guardians guardians,
		@Valid Address address,
		@Size(max = 8) String bloodGroup,
		@Size(max = 160) String previousSchool) {

	/** Deliberately loose: phone-number shapes differ by country and this code is school-agnostic. */
	private static final String PHONE = "^[+0-9][0-9 ()/-]{5,31}$";

	/** The session is not here; see the class comment. */
	public record Enrollment(
			@NotBlank String classId,
			@NotBlank @Size(max = 8) String section,
			@Min(1) @Max(9999) int rollNo) {
	}

	/** Only {@code phone} is required — it is how the school actually reaches the family. */
	public record Guardians(
			@Size(max = 120) String fatherName,
			@Size(max = 120) String motherName,
			@Size(max = 120) String guardianName,
			@NotBlank @Pattern(regexp = PHONE, message = "must be a phone number") String phone,
			@Pattern(regexp = PHONE, message = "must be a phone number") String altPhone,
			@Email @Size(max = 200) String email,
			@Size(max = 120) String occupation) {
	}

	public record Address(
			@Size(max = 200) String line1,
			@Size(max = 200) String line2,
			@Size(max = 100) String city,
			@Size(max = 100) String state,
			@Size(max = 16) String postalCode) {
	}
}

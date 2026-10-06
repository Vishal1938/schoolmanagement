package com.school.people.api;

import java.time.LocalDate;
import java.util.List;

import com.school.people.domain.EmployeeType;
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
 * Body of {@code POST /employees} and {@code PUT /employees/{uniqueId}}.
 *
 * <p>The type decides which half of the body matters: {@code qualification}, {@code subjectIds} and
 * {@code experienceYears} apply to a TEACHER, {@code designation} and {@code hasLogin} to a STAFF
 * member. The other half is <em>ignored and cleared</em> rather than rejected, so a type change
 * cannot leave a designation stuck on a teacher.
 *
 * <p>{@code uniqueId} and {@code status} are absent on purpose: the first is issued by the server
 * and never changes, the second has its own endpoint.
 */
public record EmployeeRequest(
		@NotNull EmployeeType employeeType,
		@NotBlank @Size(max = 120) String name,
		@Past LocalDate dob,
		Gender gender,
		@NotBlank @Pattern(regexp = EmployeeRequest.PHONE, message = "must be a phone number") String phone,
		@Email @Size(max = 200) String email,
		@Valid Address address,
		@NotNull LocalDate joiningDate,
		@Size(max = 512) String photoUrl,
		@Valid Bank bank,
		@Pattern(regexp = "^[0-9]{4}$", message = "must be the last four digits of the PAN") String panLast4,
		@Size(max = 200) String qualification,
		@Size(max = 40) List<@NotBlank String> subjectIds,
		@Min(0) @Max(70) Integer experienceYears,
		@Size(max = 80) String designation,
		boolean hasLogin) {

	/** Deliberately loose: phone-number shapes differ by country and this code is school-agnostic. */
	static final String PHONE = "^[+0-9][0-9 ()/-]{5,31}$";

	/**
	 * Bank details as sent in. The account number arrives in the clear exactly once, here, over the
	 * same TLS as everything else; it is encrypted before it is stored and never comes back in a list.
	 */
	public record Bank(
			@Size(max = 120) String accountName,
			@NotBlank @Pattern(regexp = "^[0-9]{6,20}$", message = "must be 6-20 digits") String accountNumber,
			@Size(max = 20) String ifsc,
			@Size(max = 120) String bankName) {
	}

	public record Address(
			@Size(max = 200) String line1,
			@Size(max = 200) String line2,
			@Size(max = 100) String city,
			@Size(max = 100) String state,
			@Size(max = 16) String postalCode) {
	}
}

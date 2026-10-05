package com.school.people.domain;

import java.time.Instant;
import java.time.LocalDate;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A student on the rolls, past or present.
 *
 * <p>{@code uniqueId} is both the student's ID and the ID of their login, issued once by the
 * {@code IdGenerator} and never changed — attendance, marks, invoices and receipts all point at it,
 * and a receipt printed three years ago has to stay readable.
 *
 * <p>Nothing about fees is stored here. The fee status a teacher sees is asked of the fees module
 * per request, so there is no second copy of it to go stale, and no amount anywhere near this
 * document.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(Student.COLLECTION)
// The class list, which is the single most-used query in the application: a class teacher opening
// their section. Roll number is in the key so the list comes back already in roll order.
@CompoundIndex(name = "students_class_section_roll_idx",
		def = "{'enrollment.classId': 1, 'enrollment.section': 1, 'enrollment.rollNo': 1}")
// Backs both the status filter and the default name ordering of the list endpoint.
@CompoundIndex(name = "students_status_name_idx", def = "{'status': 1, 'name': 1}")
// The ?q= search by family phone number.
@CompoundIndex(name = "students_guardian_phone_idx", def = "{'guardians.phone': 1}")
public class Student {

	public static final String COLLECTION = "students";

	@Id
	private String id;

	/** Also the {@code uniqueId} of this student's login, e.g. {@code DEMO-STU-26-00142}. */
	@Indexed(name = "students_unique_id_idx", unique = true)
	private String uniqueId;

	/** Indexed on its own for the {@code ?q=} name-prefix search, which sorts by name too. */
	@Indexed(name = "students_name_idx")
	private String name;

	private LocalDate dob;

	private Gender gender;

	private String photoUrl;

	/** The school's own admission register number, which predates this system. Unique. */
	@Indexed(name = "students_admission_no_idx", unique = true)
	private String admissionNo;

	private LocalDate admissionDate;

	/** Where the student sits in the current session. */
	private Enrollment enrollment;

	private Guardians guardians;

	private Address address;

	private String bloodGroup;

	private String previousSchool;

	private StudentStatus status;

	private Instant createdAt;

	private Instant updatedAt;
}

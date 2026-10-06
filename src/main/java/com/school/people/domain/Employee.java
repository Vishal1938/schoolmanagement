package com.school.people.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
 * Anyone the school employs: teachers and non-teaching staff alike, in one collection.
 *
 * <p>One document rather than two because almost everything about them is the same — the ID series,
 * attendance, payroll, salary slips — and the handful of fields that differ are governed by
 * {@link #employeeType}. The teacher-only fields are null on staff and the staff-only fields are
 * null on teachers; {@code EmployeeService} clears whichever half does not apply, so a type change
 * cannot leave a designation on a teacher.
 *
 * <p>{@code uniqueId} is shared with the login, where there is one, and never changes.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Document(Employee.COLLECTION)
// The teacher picker and the ?type=&status= filters, which are the only list queries there are.
@CompoundIndex(name = "employees_type_status_name_idx",
		def = "{'employeeType': 1, 'status': 1, 'name': 1}")
public class Employee {

	public static final String COLLECTION = "employees";

	/** Also the {@code uniqueId} of the login, where there is one. E.g. {@code DEMO-EMP-26-0007}. */
	@Indexed(name = "employees_unique_id_idx", unique = true)
	private String uniqueId;

	@Id
	private String id;

	private EmployeeType employeeType;

	/** Indexed on its own for the {@code ?q=} name-prefix search. */
	@Indexed(name = "employees_name_idx")
	private String name;

	private LocalDate dob;

	private Gender gender;

	/** Searched by {@code ?q=}, hence the index. */
	@Indexed(name = "employees_phone_idx")
	private String phone;

	/**
	 * Unlike a student's, this is the login's e-mail too: employees do get password-reset mail, and
	 * two of them cannot share an address.
	 */
	private String email;

	private Address address;

	private LocalDate joiningDate;

	private String photoUrl;

	private EmployeeStatus status;

	/** Null until somebody fills it in; see {@link BankDetails} for why it is stored as it is. */
	private BankDetails bank;

	/**
	 * Last four of the PAN, for payroll reporting. Only the last four is ever held — the full number
	 * is not needed by anything here, and not storing it is cheaper than protecting it.
	 */
	private String panLast4;

	// --- teachers only ----------------------------------------------------------------------------

	private String qualification;

	/** Ids of {@code subjects} this teacher can teach. Which class they actually take is set in B4. */
	private List<String> subjectIds;

	private Integer experienceYears;

	// --- staff only -------------------------------------------------------------------------------

	/** Free text: "Accountant", "Driver", "Peon". Schools invent their own, so it is not an enum. */
	private String designation;

	/** Whether this staff member has a login. Always effectively true for a teacher. */
	private boolean hasLogin;

	private Instant createdAt;

	private Instant updatedAt;
}

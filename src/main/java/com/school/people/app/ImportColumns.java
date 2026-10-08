package com.school.people.app;

import java.util.List;

/**
 * The column headers of the two import templates, which are also the {@code column} values reported
 * back in an {@link com.school.people.api.ImportError}.
 *
 * <p>One source of truth for three things that have to agree: the header row the template writes,
 * the headers the reader looks a cell up by, and the names an administrator sees in an error. The
 * trailing asterisk marks a required column and is part of the header text — it is stripped when
 * headers are matched, so a file whose asterisks were edited away still imports.
 */
final class ImportColumns {

	/** {@code GET /students/import/template}. */
	static final class Students {

		static final String NAME = "Name*";
		static final String DOB = "DOB*";
		static final String GENDER = "Gender*";
		static final String SCHOOL_CLASS = "Class*";
		static final String SECTION = "Section*";
		static final String ROLL_NO = "Roll No";
		static final String ADMISSION_NO = "Admission No";
		static final String ADMISSION_DATE = "Admission Date";
		static final String FATHER_NAME = "Father Name";
		static final String MOTHER_NAME = "Mother Name";
		static final String GUARDIAN_PHONE = "Guardian Phone*";
		static final String ALTERNATE_PHONE = "Alternate Phone";
		static final String EMAIL = "Email";
		static final String ADDRESS = "Address";
		static final String BLOOD_GROUP = "Blood Group";
		static final String PREVIOUS_SCHOOL = "Previous School";

		static final List<String> ALL = List.of(NAME, DOB, GENDER, SCHOOL_CLASS, SECTION, ROLL_NO, ADMISSION_NO,
				ADMISSION_DATE, FATHER_NAME, MOTHER_NAME, GUARDIAN_PHONE, ALTERNATE_PHONE, EMAIL, ADDRESS,
				BLOOD_GROUP, PREVIOUS_SCHOOL);

		private Students() {
		}
	}

	/** {@code GET /employees/import/template}. */
	static final class Employees {

		static final String TYPE = "Type*";
		static final String NAME = "Name*";
		static final String DOB = "DOB";
		static final String GENDER = "Gender";
		static final String PHONE = "Phone*";
		static final String EMAIL = "Email";
		static final String JOINING_DATE = "Joining Date*";
		static final String DESIGNATION = "Designation";
		static final String QUALIFICATION = "Qualification";
		static final String SUBJECTS = "Subjects";
		static final String CAN_LOGIN = "Can Login";
		static final String BANK_ACCOUNT_NAME = "Bank Account Name";
		static final String ACCOUNT_NUMBER = "Account Number";
		static final String IFSC = "IFSC";
		static final String BANK_NAME = "Bank Name";

		static final List<String> ALL = List.of(TYPE, NAME, DOB, GENDER, PHONE, EMAIL, JOINING_DATE, DESIGNATION,
				QUALIFICATION, SUBJECTS, CAN_LOGIN, BANK_ACCOUNT_NAME, ACCOUNT_NUMBER, IFSC, BANK_NAME);

		private Employees() {
		}
	}

	private ImportColumns() {
	}
}

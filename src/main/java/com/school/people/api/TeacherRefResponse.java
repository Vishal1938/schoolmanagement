package com.school.people.api;

import com.school.people.domain.Employee;

/** One entry of the teacher picker: enough to show a name and send back an id. */
public record TeacherRefResponse(String uniqueId, String name) {

	public static TeacherRefResponse of(Employee employee) {
		return new TeacherRefResponse(employee.getUniqueId(), employee.getName());
	}
}

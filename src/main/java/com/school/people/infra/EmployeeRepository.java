package com.school.people.infra;

import java.util.List;
import java.util.Optional;

import com.school.people.domain.Employee;
import com.school.people.domain.EmployeeStatus;
import com.school.people.domain.EmployeeType;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for {@code employees}. Internal to this module: everybody else goes through
 * {@code EmployeeService}.
 *
 * <p>As with students, the list query is built in the service with {@code MongoOperations} — three
 * optional filters plus a two-field {@code ?q=} prefix search is past what a derived method can say.
 */
public interface EmployeeRepository extends MongoRepository<Employee, String> {

	Optional<Employee> findByUniqueId(String uniqueId);

	boolean existsByUniqueId(String uniqueId);

	/** The teacher picker behind {@code GET /users/teachers}. */
	List<Employee> findByEmployeeTypeAndStatusOrderByNameAsc(EmployeeType employeeType, EmployeeStatus status);

	/** Everyone currently employed, for the daily employee attendance register (B8). */
	List<Employee> findByStatusOrderByNameAsc(EmployeeStatus status);
}

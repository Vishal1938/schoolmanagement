package com.school.payroll.infra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.school.payroll.domain.SalaryStructure;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for {@code salary_structures}. Internal to this module; callers use
 * {@code SalaryStructureService}.
 */
public interface SalaryStructureRepository extends MongoRepository<SalaryStructure, String> {

	/** The whole history for one employee, newest version first. */
	List<SalaryStructure> findByEmployeeUniqueIdOrderByVersionDesc(String employeeUniqueId);

	/**
	 * The version in force on a date: the latest {@code effectiveFrom} not after it, and the highest
	 * version among any that share that date — a correction saved the same day wins.
	 */
	Optional<SalaryStructure> findFirstByEmployeeUniqueIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionDesc(
			String employeeUniqueId, LocalDate on);

	/** The highest version so far, for numbering the next one. */
	Optional<SalaryStructure> findFirstByEmployeeUniqueIdOrderByVersionDesc(String employeeUniqueId);
}

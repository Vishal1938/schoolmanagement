package com.school.payroll.infra;

import java.util.List;

import com.school.payroll.domain.AdvanceStatus;
import com.school.payroll.domain.SalaryAdvance;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for {@code salary_advances}. Internal to this module; callers use
 * {@code SalaryAdvanceService}.
 */
public interface SalaryAdvanceRepository extends MongoRepository<SalaryAdvance, String> {

	/** One employee's advances, newest first — what the office looks at. */
	List<SalaryAdvance> findByEmployeeUniqueIdOrderByGivenOnDesc(String employeeUniqueId);

	/** Everything still being recovered, newest first. */
	List<SalaryAdvance> findByStatusOrderByGivenOnDesc(AdvanceStatus status);

	/** What a run deducts from: still owed, oldest advance first so the earliest clears first. */
	List<SalaryAdvance> findByEmployeeUniqueIdAndStatusOrderByGivenOnAsc(String employeeUniqueId,
			AdvanceStatus status);
}

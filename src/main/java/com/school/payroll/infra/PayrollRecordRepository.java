package com.school.payroll.infra;

import java.util.List;

import com.school.payroll.domain.PayrollRecord;
import com.school.payroll.domain.PayrollStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for {@code payroll_records}. Internal to this module; callers use
 * {@code PayrollService}.
 */
public interface PayrollRecordRepository extends MongoRepository<PayrollRecord, String> {

	/** One month's register, in the order the run wrote it: by employee name. */
	List<PayrollRecord> findByMonthOrderByEmployeeNameAsc(String month);

	/** One employee's slips, newest month first. Behind {@code GET /payroll/mine}. */
	List<PayrollRecord> findByEmployeeUniqueIdOrderByMonthDesc(String employeeUniqueId);

	/**
	 * This employee's records in a given state. The run reads the PENDING ones to see which advance
	 * installments it has already planned but not yet taken off the balance.
	 */
	List<PayrollRecord> findByEmployeeUniqueIdAndStatus(String employeeUniqueId, PayrollStatus status);
}

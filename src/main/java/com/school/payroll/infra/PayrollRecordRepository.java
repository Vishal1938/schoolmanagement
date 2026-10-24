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

	/**
	 * Every record from one month to another, both ends inclusive.
	 *
	 * <p>A plain string range, which works because {@code yyyy-MM} sorts chronologically as text — see
	 * {@code Months}. Backs the demonstration data guard and wipe (B20), both of which are about "the
	 * months this academic session covers".
	 */
	List<PayrollRecord> findByMonthBetweenOrderByMonthAsc(String from, String to);

	/** One employee's slips, newest month first. Behind {@code GET /payroll/mine}. */
	List<PayrollRecord> findByEmployeeUniqueIdOrderByMonthDesc(String employeeUniqueId);

	/**
	 * This employee's records in a given state. The run reads the PENDING ones to see which advance
	 * installments it has already planned but not yet taken off the balance.
	 */
	List<PayrollRecord> findByEmployeeUniqueIdAndStatus(String employeeUniqueId, PayrollStatus status);

	/**
	 * Every record in one state, in the order the money went out — the accounts backfill (B21), which
	 * books salaries chronologically.
	 */
	List<PayrollRecord> findByStatusOrderByPaidOnAsc(PayrollStatus status);
}

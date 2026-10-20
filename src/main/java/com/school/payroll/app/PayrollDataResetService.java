package com.school.payroll.app;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import com.school.payroll.domain.PayrollRecord;
import com.school.payroll.domain.SalaryAdvance;
import com.school.payroll.domain.SalaryStructure;
import com.school.payroll.infra.PayrollRecordRepository;
import com.school.payroll.infra.SalaryAdvanceRepository;
import com.school.payroll.infra.SalaryStructureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Wipes the payroll side of the demonstration data set (B20): salary structures, advances and the
 * records of a range of months.
 *
 * <p>Here rather than in the demonstration module for the usual reason — the repositories are this
 * module's and stay in {@code infra}.
 *
 * <p><strong>The three are scoped differently, and that is not an oversight.</strong> A payroll record
 * belongs to a month, so only the months of the session being reset are removed, and a school that
 * has run last year's payroll keeps it. A salary structure and an advance belong to a <em>person</em>
 * and have no month at all: "Dr. Rekha Singh is on ₹45,000 from October" is not a fact about a
 * session. Leaving them behind would make the next seed a no-op that quietly reused last run's
 * figures, so they go whole — which is safe precisely because the only caller is a feature a real
 * deployment never switches on.
 *
 * <p>Deleting a PAID record is something {@link PayrollService#delete} refuses outright, and rightly:
 * a salary that went out is a fact. This bypasses that rule knowingly. It is the whole demonstration
 * data set going at once, not a correction to one person's pay.
 */
@Service
public class PayrollDataResetService {

	private static final Logger log = LoggerFactory.getLogger(PayrollDataResetService.class);

	private final PayrollRecordRepository records;
	private final SalaryStructureRepository structures;
	private final SalaryAdvanceRepository advances;

	public PayrollDataResetService(PayrollRecordRepository records, SalaryStructureRepository structures,
			SalaryAdvanceRepository advances) {
		this.records = records;
		this.structures = structures;
		this.advances = advances;
	}

	/**
	 * Removes the records of every month from {@code from} to {@code to} inclusive, and every salary
	 * structure and advance there is.
	 *
	 * <p>Not transactional: a half-finished wipe is safe, because running it again removes what is
	 * still there.
	 */
	public PayrollDataPurge purge(YearMonth from, YearMonth to) {
		List<PayrollRecord> computed = records.findByMonthBetweenOrderByMonthAsc(Months.format(from),
				Months.format(to));
		List<SalaryStructure> agreed = structures.findAll();
		List<SalaryAdvance> given = advances.findAll();

		records.deleteAll(computed);
		structures.deleteAll(agreed);
		advances.deleteAll(given);

		PayrollDataPurge purge = new PayrollDataPurge(computed.size(), agreed.size(), given.size());
		log.info("Wiped payroll from {} to {}: {}", from, to, purge);
		return purge;
	}

	/** What a wipe removed, per collection. */
	public record PayrollDataPurge(int payrollRecords, int salaryStructures, int salaryAdvances) {

		/** For the response and the audit entry, where the keys are the collection names. */
		public Map<String, Integer> asCounts() {
			return Map.of("payrollRecords", payrollRecords, "salaryStructures", salaryStructures,
					"salaryAdvances", salaryAdvances);
		}
	}
}

package com.school.accounts.app;

import java.util.List;

import com.school.accounts.api.BackfillResponse;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.payment.app.PaymentService;
import com.school.payment.domain.Payment;
import com.school.payroll.app.PayrollService;
import com.school.payroll.app.SalaryAdvanceService;
import com.school.payroll.domain.AdvanceRecovery;
import com.school.payroll.domain.PayrollRecord;
import com.school.payroll.domain.SalaryAdvance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Writes the automatic entries for everything that happened before the books existed.
 *
 * <p>Needed because the expense book is a module a deployment can switch on at any point: a school
 * that has been collecting fees and paying salaries for a year would otherwise open the accounts
 * screens to an empty day book, with no way to produce a figure for a month that has already gone.
 *
 * <p><strong>Safe to run more than once, and on a live deployment.</strong> Every writer underneath
 * is keyed on {@code sourceRef} ({@link AutomaticEntryService}), so a second run books nothing and
 * reports it as skipped. There is no "wipe and rebuild" — it only ever adds what is missing, so a
 * manual correction to a money account on a backfilled line survives the next run.
 *
 * <p>It reads the history through the other modules' own services, never their repositories, which
 * is why {@code PaymentService.capturedPayments()} and friends exist.
 */
@Service
public class AccountsBackfillService {

	private static final Logger log = LoggerFactory.getLogger(AccountsBackfillService.class);

	private final AutomaticEntryService automatic;
	private final PaymentService payments;
	private final PayrollService payroll;
	private final SalaryAdvanceService advances;
	private final AccountsSeeder seeder;
	private final AuditService audit;

	public AccountsBackfillService(AutomaticEntryService automatic, PaymentService payments, PayrollService payroll,
			SalaryAdvanceService advances, AccountsSeeder seeder, AuditService audit) {
		this.automatic = automatic;
		this.payments = payments;
		this.payroll = payroll;
		this.advances = advances;
		this.seeder = seeder;
		this.audit = audit;
	}

	/**
	 * Walks every captured fee payment, every salary paid and every advance, booking what is not
	 * booked yet.
	 *
	 * <p><strong>Not one transaction</strong>, and deliberately not: a year of a real school is
	 * thousands of entries, which would exceed Atlas's sixty-second transaction limit and would have
	 * to be re-run from the start on any failure. Each entry is independent and idempotent, so a run
	 * that dies halfway has written half the entries and the next run finishes the job.
	 *
	 * @return what was written and what was already there, per kind
	 */
	public BackfillResponse run() {
		seeder.ensureSeeded();
		Counts fees = backfillFees();
		Counts salaries = backfillSalaries();
		Counts advancesGiven = backfillAdvances();
		Counts recoveries = backfillRecoveries();

		BackfillResponse response = new BackfillResponse(
				fees.written, fees.skipped,
				salaries.written, salaries.skipped,
				advancesGiven.written, advancesGiven.skipped,
				recoveries.written, recoveries.skipped);
		audit.record(AuditAction.ACCOUNTS_BACKFILLED, "Accounts", "accounts", null, response);
		log.info("Accounts backfill complete: {}", response);
		return response;
	}

	private Counts backfillFees() {
		Counts counts = new Counts();
		for (Payment payment : payments.capturedPayments()) {
			counts.count(automatic.bookFeePayment(payment.getId(), payment.getReceiptNo(),
					payment.getStudentUniqueId(), payment.getPayerName(), payment.getAmount(), payment.getMode(),
					payment.getPaidAt()));
		}
		return counts;
	}

	private Counts backfillSalaries() {
		Counts counts = new Counts();
		for (PayrollRecord record : payroll.paidRecords()) {
			counts.count(automatic.bookSalary(record.getId(), record.getEmployeeUniqueId(), record.getEmployeeName(),
					record.getMonth(), record.getNet(), record.getMode(), record.getReference(), record.getPaidOn()));
		}
		return counts;
	}

	private Counts backfillAdvances() {
		Counts counts = new Counts();
		for (SalaryAdvance advance : advances.all()) {
			counts.count(automatic.bookAdvanceGiven(advance.getId(), advance.getEmployeeUniqueId(),
					advance.getEmployeeName(), advance.getAmount(), advance.getGivenOn(), advance.getReason()));
		}
		return counts;
	}

	/**
	 * Recoveries, read off the paid payroll records rather than off the advances.
	 *
	 * <p>An advance stores {@code recoveredSoFar} as one running total, which cannot be split back
	 * into the months it came off. A paid record, on the other hand, names the advances it deducted
	 * from and how much — so the records are the only place the history exists, and that is where this
	 * reads it.
	 *
	 * <p>The amount is the installment the run <em>planned</em>, which is what the record holds; a
	 * live recovery books what was actually taken, which can be a little less on the last
	 * installment. The {@code sourceRef} is the same either way, so the two can never double up — the
	 * backfill simply never corrects a figure the live path already wrote.
	 */
	private Counts backfillRecoveries() {
		Counts counts = new Counts();
		for (PayrollRecord record : payroll.paidRecords()) {
			List<AdvanceRecovery> recoveries = record.getAdvanceRecoveries() == null
					? List.of()
					: record.getAdvanceRecoveries();
			for (AdvanceRecovery recovery : recoveries) {
				if (recovery.amount() <= 0L) {
					continue;
				}
				counts.count(automatic.bookAdvanceRecovered(recovery.advanceId(), record.getId(),
						record.getEmployeeUniqueId(), record.getEmployeeName(), recovery.amount(),
						record.getPaidOn()));
			}
		}
		return counts;
	}

	/** Written-and-skipped tally for one kind of record. */
	private static final class Counts {

		private int written;
		private int skipped;

		private void count(AutomaticEntryService.Booked booked) {
			if (booked.created()) {
				written++;
			}
			else {
				skipped++;
			}
		}
	}
}

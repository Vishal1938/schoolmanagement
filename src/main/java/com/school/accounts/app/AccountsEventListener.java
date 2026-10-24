package com.school.accounts.app;

import com.school.payment.app.PaymentCapturedEvent;
import com.school.payroll.app.PayrollRecordPaidEvent;
import com.school.payroll.app.SalaryAdvanceGivenEvent;
import com.school.payroll.app.SalaryAdvanceRecoveredEvent;
import com.school.schoolconfig.app.ModuleGate;
import com.school.schoolconfig.domain.SchoolModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps the books in step with the rest of the application, by listening rather than by being
 * called.
 *
 * <p>This is the whole of the accounts module's inbound coupling, and the direction matters: fees,
 * payment and payroll do not know the books exist, which is what CLAUDE.md means by publishing an
 * event for a cross-module side effect.
 *
 * <p><strong>{@code AFTER_COMMIT}, and failures are swallowed.</strong> Both are deliberate.
 * Booking an entry must never roll back the payment or the salary that caused it — the money has
 * moved, and a failed receipt is a worse outcome than a line missing from the expense book, which
 * {@code POST /accounts/backfill} can put right at any time. The failure is logged at error level so
 * it is not silent.
 *
 * <p>{@code fallbackExecution = true} because not every publisher is inside a transaction: an
 * advance is written without one, and without the fallback that event would be dropped on the floor
 * rather than handled immediately.
 *
 * <p>The module switch is checked here rather than by the interceptor, because an event has no path
 * for a pattern to match. A deployment with the books off therefore accumulates nothing, and
 * switching them on later starts with a backfill.
 */
@Component
public class AccountsEventListener {

	private static final Logger log = LoggerFactory.getLogger(AccountsEventListener.class);

	private final AutomaticEntryService automatic;
	private final ModuleGate modules;

	public AccountsEventListener(AutomaticEntryService automatic, ModuleGate modules) {
		this.automatic = automatic;
		this.modules = modules;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onPaymentCaptured(PaymentCapturedEvent event) {
		book("fee payment " + event.paymentId(), () -> automatic.bookFeePayment(event.paymentId(), event.receiptNo(),
				event.studentUniqueId(), event.payerName(), event.amount(), event.mode(), event.paidAt()));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onSalaryPaid(PayrollRecordPaidEvent event) {
		book("salary " + event.recordId(), () -> automatic.bookSalary(event.recordId(), event.employeeUniqueId(),
				event.employeeName(), event.month(), event.net(), event.mode(), event.reference(), event.paidOn()));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onAdvanceGiven(SalaryAdvanceGivenEvent event) {
		book("advance " + event.advanceId(), () -> automatic.bookAdvanceGiven(event.advanceId(),
				event.employeeUniqueId(), event.employeeName(), event.amount(), event.givenOn(), event.reason()));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onAdvanceRecovered(SalaryAdvanceRecoveredEvent event) {
		book("advance recovery " + event.advanceId(), () -> automatic.bookAdvanceRecovered(event.advanceId(),
				event.payrollRecordId(), event.employeeUniqueId(), event.employeeName(), event.amount(),
				event.recoveredOn()));
	}

	/**
	 * Runs one booking behind the module switch, turning any failure into a log line rather than an
	 * exception the publisher would see.
	 */
	private void book(String what, Runnable booking) {
		try {
			// Inside the try, not before it: reading the switch reads school_config, which a deployment
			// mid-first-boot may not have yet — and with fallbackExecution an exception thrown here
			// would reach the publisher rather than being logged by the transaction synchronization.
			if (!modules.isEnabled(SchoolModule.ACCOUNTS)) {
				return;
			}
			booking.run();
		}
		catch (RuntimeException ex) {
			log.error("The accounts entry for {} could not be written. The money is recorded in its own "
					+ "module; run POST /accounts/backfill to book it.", what, ex);
		}
	}
}

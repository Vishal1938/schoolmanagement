package com.school.accounts.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.school.accounts.domain.AccountCategory;
import com.school.accounts.domain.AccountDefaults;
import com.school.accounts.domain.AccountEntry;
import com.school.accounts.domain.EntryMode;
import com.school.accounts.domain.EntrySource;
import com.school.accounts.domain.EntryStatus;
import com.school.accounts.domain.EntryType;
import com.school.accounts.domain.SystemCategory;
import com.school.accounts.infra.AccountEntryRepository;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.payment.domain.PaymentMode;
import com.school.payroll.domain.SalaryPaymentMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * The entries the application writes for itself: a fee collection, a salary, an advance and its
 * recovery.
 *
 * <p><strong>Everything here is idempotent, keyed on {@code sourceRef}.</strong> The events that
 * drive it can be delivered more than once and the backfill may be run twice, so each writer first
 * looks for an entry under that key and returns it untouched if one exists; the unique sparse index
 * catches the remaining race, two callers getting past the read together. That is what lets
 * {@code POST /accounts/backfill} be run on a live deployment without double-counting a single
 * rupee.
 *
 * <p>These entries are <strong>not held to the month lock</strong>. The money has already moved — a
 * payment taken today, a salary backdated into a closed month — and refusing to book it would either
 * fail the payment or leave the books quietly short. The lock exists to stop a person rewriting a
 * closed period, which it still does.
 *
 * <p>A missing default account is <strong>not</strong> a refusal either. The entry is written with
 * no {@code moneyAccountId}, which shows up as unassigned and can be corrected by setting the
 * defaults and re-running the backfill — losing the line altogether would be worse than having one
 * nobody has filed yet.
 */
@Service
public class AutomaticEntryService {

	private static final Logger log = LoggerFactory.getLogger(AutomaticEntryService.class);

	private final AccountEntryRepository entries;
	private final AccountCategoryService categories;
	private final AccountSettingsService settings;
	private final AuditService audit;
	private final Clock clock;

	public AutomaticEntryService(AccountEntryRepository entries, AccountCategoryService categories,
			AccountSettingsService settings, AuditService audit, Clock clock) {
		this.entries = entries;
		this.categories = categories;
		this.settings = settings;
		this.audit = audit;
		this.clock = clock;
	}

	// --- fees -------------------------------------------------------------------------------------

	/**
	 * Books a captured fee payment as income against "Fees collected".
	 *
	 * <p>The account is the cash default for money taken in the drawer and the bank default for
	 * everything else, because that is where each physically lands: a cheque, a UPI reference, a
	 * transfer and a gateway settlement all arrive in a bank account.
	 *
	 * @param paidAt when the money moved; the entry is dated the day of it in the school's own zone
	 * @return the entry, with {@code created} false when one was already there
	 */
	public Booked bookFeePayment(String paymentId, String receiptNo, String studentUniqueId, String payerName,
			long amount, PaymentMode mode, Instant paidAt) {
		AccountDefaults defaults = settings.defaults();
		boolean cash = mode == PaymentMode.CASH;
		String description = receiptNo == null
				? "Fees received from " + studentUniqueId
				: "Receipt " + receiptNo + " — " + studentUniqueId;
		return write(AccountEntry.builder()
				.type(EntryType.INCOME)
				.date(dateOf(paidAt))
				.amount(amount)
				.party(payerName == null || payerName.isBlank() ? studentUniqueId : payerName)
				.description(description)
				.moneyAccountId(cash ? defaults.cashFeesTo() : defaults.bankFeesTo())
				.mode(feeMode(mode))
				.reference(receiptNo)
				.status(EntryStatus.PAID)
				.paidOn(dateOf(paidAt))
				.source(EntrySource.FEES)
				.sourceRef("payment:" + paymentId), SystemCategory.FEES_COLLECTED);
	}

	// --- payroll ----------------------------------------------------------------------------------

	/**
	 * Books a salary that has gone out as an expense against "Salaries".
	 *
	 * <p>The <strong>net</strong>, not the gross: deductions and the advance installment never leave
	 * the school, so booking the gross would overstate the month by every rupee it withheld. The
	 * advance installment has its own entry on the other side of the asset category.
	 */
	public Booked bookSalary(String recordId, String employeeUniqueId, String employeeName, String month,
			long net, SalaryPaymentMode mode, String reference, LocalDate paidOn) {
		return write(AccountEntry.builder()
				.type(EntryType.EXPENSE)
				.date(paidOn)
				.amount(net)
				.party(employeeName == null ? employeeUniqueId : employeeName)
				.description("Salary for " + month + " — " + employeeUniqueId)
				.moneyAccountId(settings.defaults().salariesFrom())
				.mode(salaryMode(mode))
				.reference(reference)
				.status(EntryStatus.PAID)
				.paidOn(paidOn)
				.source(EntrySource.PAYROLL)
				.sourceRef("payroll:" + recordId), SystemCategory.SALARIES);
	}

	/**
	 * Books an advance handed over, on the expense side of the "Staff advances" asset category.
	 *
	 * <p>Cash has left a drawer, so a money account's balance has to move; but the school is owed it
	 * back, so it is not a cost. {@link SystemCategory#STAFF_ADVANCES} is marked as an asset and every
	 * report leaves it out of both totals — see {@link AccountsSummaryService}.
	 */
	public Booked bookAdvanceGiven(String advanceId, String employeeUniqueId, String employeeName, long amount,
			LocalDate givenOn, String reason) {
		return write(AccountEntry.builder()
				.type(EntryType.EXPENSE)
				.date(givenOn)
				.amount(amount)
				.party(employeeName == null ? employeeUniqueId : employeeName)
				.description(reason == null || reason.isBlank()
						? "Advance to " + employeeUniqueId
						: "Advance to " + employeeUniqueId + " — " + reason)
				.moneyAccountId(settings.defaults().salariesFrom())
				.status(EntryStatus.PAID)
				.paidOn(givenOn)
				.source(EntrySource.ADVANCE)
				.sourceRef("advance:" + advanceId), SystemCategory.STAFF_ADVANCES);
	}

	/**
	 * Books an installment of an advance coming back, on the income side of the same asset category —
	 * so it reduces what the school is owed rather than earning anything.
	 *
	 * <p>No money account and no mode: nothing physically moved. The installment was withheld from a
	 * salary, and the salary's own entry is already the net that went out.
	 */
	public Booked bookAdvanceRecovered(String advanceId, String payrollRecordId, String employeeUniqueId,
			String employeeName, long amount, LocalDate recoveredOn) {
		return write(AccountEntry.builder()
				.type(EntryType.INCOME)
				.date(recoveredOn)
				.amount(amount)
				.party(employeeName == null ? employeeUniqueId : employeeName)
				.description("Advance recovered from " + employeeUniqueId + "'s salary")
				.status(EntryStatus.PAID)
				.paidOn(recoveredOn)
				.source(EntrySource.ADVANCE)
				.sourceRef("advance-recovery:" + advanceId + ":" + payrollRecordId), SystemCategory.STAFF_ADVANCES);
	}

	// --- internals --------------------------------------------------------------------------------

	/**
	 * Writes the entry unless one already exists under its {@code sourceRef}.
	 *
	 * <p>The read-then-insert is the normal path and the {@link DuplicateKeyException} is the race:
	 * two webhook deliveries, or a webhook and the backfill, reaching here together. Both answers are
	 * the entry that exists, because the thing it records happened once.
	 */
	private Booked write(AccountEntry.AccountEntryBuilder builder, SystemCategory system) {
		AccountCategory category = categories.requireSystem(system);
		Instant now = Instant.now(clock);
		AccountEntry candidate = builder
				.categoryId(category.getId())
				.attachments(List.of())
				.createdAt(now)
				.updatedAt(now)
				.build();

		Optional<AccountEntry> existing = entries.findBySourceRef(candidate.getSourceRef());
		if (existing.isPresent()) {
			log.debug("Accounts entry for {} already exists; nothing booked", candidate.getSourceRef());
			return new Booked(existing.get(), false);
		}
		try {
			AccountEntry saved = entries.insert(candidate);
			audit.record(AuditAction.ACCOUNT_ENTRY_CREATED, AccountEntryService.AUDIT_ENTITY, saved.getId(),
					null, saved);
			return new Booked(saved, true);
		}
		catch (DuplicateKeyException ex) {
			// Somebody else booked it between the read and the insert. Theirs is the entry.
			return new Booked(entries.findBySourceRef(candidate.getSourceRef()).orElseThrow(() -> ex), false);
		}
	}

	/**
	 * One booking's outcome.
	 *
	 * @param created false when an entry was already there under that {@code sourceRef} — the normal
	 *                answer for a repeated webhook or a second backfill, and what the backfill counts
	 *                as skipped
	 */
	public record Booked(AccountEntry entry, boolean created) {
	}

	/** The day a timestamp falls on in the school's own zone, which is what a report groups by. */
	private LocalDate dateOf(Instant at) {
		return at == null ? LocalDate.now(clock) : LocalDate.ofInstant(at, clock.getZone());
	}

	/**
	 * The fees module's mode as the books record it. {@code ONLINE} becomes {@code BANK_TRANSFER}:
	 * what the gateway does is settle into a bank account, and the books care where the money is
	 * rather than which checkout a family used.
	 */
	private static EntryMode feeMode(PaymentMode mode) {
		if (mode == null) {
			return null;
		}
		return switch (mode) {
			case CASH -> EntryMode.CASH;
			case CHEQUE -> EntryMode.CHEQUE;
			case UPI_OFFLINE -> EntryMode.UPI;
			case BANK_TRANSFER, ONLINE -> EntryMode.BANK_TRANSFER;
		};
	}

	private static EntryMode salaryMode(SalaryPaymentMode mode) {
		if (mode == null) {
			return null;
		}
		return switch (mode) {
			case CASH -> EntryMode.CASH;
			case CHEQUE -> EntryMode.CHEQUE;
			case UPI -> EntryMode.UPI;
			case BANK_TRANSFER -> EntryMode.BANK_TRANSFER;
		};
	}
}

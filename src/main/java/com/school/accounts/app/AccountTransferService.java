package com.school.accounts.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.school.accounts.api.TransferRequest;
import com.school.accounts.domain.AccountTransfer;
import com.school.accounts.domain.MoneyAccount;
import com.school.accounts.infra.AccountTransferRepository;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.BusinessRuleException;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ValidationException;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Money moved between two of the school's own accounts — the week's cash banked, a sweep between
 * bank accounts.
 *
 * <p><strong>Neither income nor expense</strong>, which is the whole reason this is a service of its
 * own: nothing was earned and nothing was spent, so a transfer must not appear in any month's
 * totals. It moves two balances and that is all.
 *
 * <p>Append-only. A transfer entered the wrong way round is corrected by one going back, so that
 * both statements show what actually happened — the same rule payments follow.
 */
@Service
public class AccountTransferService {

	static final String AUDIT_ENTITY = "AccountTransfer";

	/** Stand-ins for an open-ended range; see {@link #list}. Both are well inside MongoDB's date range. */
	private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);

	private static final LocalDate LATEST = LocalDate.of(9999, 12, 31);

	private final AccountTransferRepository transfers;
	private final MoneyAccountService accounts;
	private final AccountSettingsService settings;
	private final AccountsSeeder seeder;
	private final AuditService audit;
	private final Clock clock;

	public AccountTransferService(AccountTransferRepository transfers, MoneyAccountService accounts,
			AccountSettingsService settings, AccountsSeeder seeder, AuditService audit, Clock clock) {
		this.transfers = transfers;
		this.accounts = accounts;
		this.settings = settings;
		this.seeder = seeder;
		this.audit = audit;
		this.clock = clock;
	}

	/** The transfer list, newest first, optionally narrowed to a date range (both ends inclusive). */
	public Page<AccountTransfer> list(LocalDate from, LocalDate to, Pageable pageable) {
		seeder.ensureSeeded();
		Pageable effective = pageable.getSort().isSorted()
				? pageable
				: PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
						Sort.by(Sort.Direction.DESC, "date"));
		if (from == null && to == null) {
			return transfers.findAllByOrderByDateDesc(effective);
		}
		// An open end becomes a sentinel, so one query serves all three cases. Deliberately not
		// LocalDate.MIN or MAX: those are year ±999999999, which overflows when Spring Data converts a
		// date to the 64-bit milliseconds MongoDB stores, and the query would throw rather than match.
		return transfers.findBetween(from == null ? EARLIEST : from, to == null ? LATEST : to, effective);
	}

	/** Account id to name for a page of transfers, in one read rather than two per row. */
	public Map<String, String> accountNamesOf(Collection<AccountTransfer> page) {
		return accounts.namesOf(page.stream()
				.flatMap(transfer -> List.of(transfer.getFromAccountId(), transfer.getToAccountId()).stream())
				.distinct()
				.toList());
	}

	/**
	 * Records a transfer.
	 *
	 * <p>It does <strong>not</strong> check that the source account has the money. A cash book can
	 * legitimately go briefly negative on paper — a deposit entered before the takings it came from, a
	 * bank account in overdraft — and refusing the entry would make the books disagree with the
	 * passbook, which is the one thing they must not do. The balance is computed and visible, so an
	 * account that should not be negative shows up as negative.
	 */
	public AccountTransfer create(TransferRequest request) {
		seeder.ensureSeeded();
		LocalDate date = request.date();
		validate(request, date);
		settings.requireUnlocked(date);
		MoneyAccount from = requireUsableAccount(request.fromAccountId(), "fromAccountId");
		MoneyAccount to = requireUsableAccount(request.toAccountId(), "toAccountId");

		AccountTransfer saved = transfers.insert(AccountTransfer.builder()
				.date(date)
				.fromAccountId(from.getId())
				.toAccountId(to.getId())
				.amount(request.amount())
				.reference(trimToNull(request.reference()))
				.note(trimToNull(request.note()))
				.recordedBy(CurrentUser.principal().map(AuthPrincipal::uniqueId).orElse(null))
				.createdAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.ACCOUNT_TRANSFER_CREATED, AUDIT_ENTITY, saved.getId(), null, saved);
		return saved;
	}

	private void validate(TransferRequest request, LocalDate date) {
		if (request.fromAccountId().equals(request.toAccountId())) {
			throw new ValidationException("A transfer needs two different accounts",
					List.of(new FieldViolation("toAccountId", "is the same account the money is coming from")));
		}
		if (date.isAfter(LocalDate.now(clock))) {
			throw new ValidationException("The transfer date is in the future",
					List.of(new FieldViolation("date", "the books record money that has moved, not money that will")));
		}
	}

	private MoneyAccount requireUsableAccount(String id, String field) {
		MoneyAccount account = accounts.find(id)
				.orElseThrow(() -> new ValidationException("That money account does not exist",
						List.of(new FieldViolation(field, "is not a money account of this school"))));
		if (!account.isActive()) {
			throw new BusinessRuleException(account.getName()
					+ " has been retired, so money cannot be moved through it.");
		}
		return account;
	}

	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

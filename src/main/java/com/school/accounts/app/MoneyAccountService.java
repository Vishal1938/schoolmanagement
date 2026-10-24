package com.school.accounts.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.school.accounts.api.MoneyAccountRequest;
import com.school.accounts.domain.AccountEntry;
import com.school.accounts.domain.AccountTransfer;
import com.school.accounts.domain.EntryStatus;
import com.school.accounts.domain.MoneyAccount;
import com.school.accounts.domain.MoneyAccountType;
import com.school.accounts.infra.AccountEntryRepository;
import com.school.accounts.infra.AccountTransferRepository;
import com.school.accounts.infra.MoneyAccountRepository;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.NotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Money accounts — the cash drawer and the bank accounts — and their balances.
 *
 * <p>A balance is <strong>computed, never stored</strong>: the opening balance, plus every settled
 * entry booked to the account, plus every transfer in, less every transfer out. A stored running
 * total would have to be right after each of a dozen write paths, including the automatic ones, and
 * the first time it was not the error would be invisible. Unpaid bills are left out — a bill the
 * school has not paid has moved no money.
 */
@Service
public class MoneyAccountService {

	static final String AUDIT_ENTITY = "MoneyAccount";

	private final MoneyAccountRepository accounts;
	private final AccountEntryRepository entries;
	private final AccountTransferRepository transfers;
	private final AccountsSeeder seeder;
	private final AuditService audit;
	private final Clock clock;

	public MoneyAccountService(MoneyAccountRepository accounts, AccountEntryRepository entries,
			AccountTransferRepository transfers, AccountsSeeder seeder, AuditService audit, Clock clock) {
		this.accounts = accounts;
		this.entries = entries;
		this.transfers = transfers;
		this.seeder = seeder;
		this.audit = audit;
		this.clock = clock;
	}

	// --- reading ----------------------------------------------------------------------------------

	/** Every account by name, or only the active ones. */
	public List<MoneyAccount> list(Boolean active) {
		seeder.ensureSeeded();
		return active == null ? accounts.findAllByOrderByNameAsc() : accounts.findByActiveOrderByNameAsc(active);
	}

	public MoneyAccount get(String id) {
		return accounts.findById(id).orElseThrow(() -> NotFoundException.of("Money account", id));
	}

	/** For a caller that has a reasonable answer for "no such account" — the settings validation. */
	Optional<MoneyAccount> find(String id) {
		return id == null || id.isBlank() ? Optional.empty() : accounts.findById(id);
	}

	/** Account id to name for these ids, in one read. Ids that no longer exist are simply absent. */
	Map<String, String> namesOf(Collection<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return Map.of();
		}
		Map<String, String> names = new LinkedHashMap<>();
		accounts.findAllById(ids).forEach(account -> names.put(account.getId(), account.getName()));
		return names;
	}

	/**
	 * What is in one account right now, in paise. See the class comment for why this is a computation
	 * and not a field.
	 */
	public long balanceOf(String accountId) {
		MoneyAccount account = get(accountId);
		long settled = entries.findSettledForAccount(accountId, EntryStatus.PAID).stream()
				.mapToLong(AccountEntry::signedAmount)
				.sum();
		long out = transfers.findByFromAccountId(accountId).stream().mapToLong(AccountTransfer::getAmount).sum();
		long in = transfers.findByToAccountId(accountId).stream().mapToLong(AccountTransfer::getAmount).sum();
		return account.getOpeningBalance() + settled + in - out;
	}

	// --- writing ----------------------------------------------------------------------------------

	public MoneyAccount create(MoneyAccountRequest request) {
		seeder.ensureSeeded();
		String name = request.name().trim();
		if (accounts.existsByName(name)) {
			throw new ConflictException("A money account named " + name + " already exists");
		}
		Instant now = Instant.now(clock);
		MoneyAccount saved = insert(apply(MoneyAccount.builder(), request)
				.name(name)
				.openingDate(request.openingDate() == null ? LocalDate.now(clock) : request.openingDate())
				.createdAt(now)
				.updatedAt(now)
				.build());
		audit.record(AuditAction.MONEY_ACCOUNT_CREATED, AUDIT_ENTITY, saved.getId(), null, saved);
		return saved;
	}

	/**
	 * Replaces an account's details, including its opening balance.
	 *
	 * <p>The opening balance stays editable on purpose: it is the one figure a school never has right
	 * on the first day, and correcting it is how the computed balance comes to agree with the
	 * passbook. Every change to it is in the trail, before and after.
	 */
	public MoneyAccount update(String id, MoneyAccountRequest request) {
		MoneyAccount before = get(id);
		String name = request.name().trim();
		if (accounts.existsByNameAndIdNot(name, id)) {
			throw new ConflictException("Another money account is named " + name);
		}
		MoneyAccount saved = accounts.save(apply(before.toBuilder(), request)
				.name(name)
				.openingDate(request.openingDate() == null ? before.getOpeningDate() : request.openingDate())
				.updatedAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.MONEY_ACCOUNT_UPDATED, AUDIT_ENTITY, id, before, saved);
		return saved;
	}

	/**
	 * Removes an account, but only while nothing has ever been booked to it.
	 *
	 * <p>A mistyped account created a minute ago can simply go. One with history cannot: its entries
	 * name it, and a balance computed against an account that has vanished is a figure nobody can
	 * explain. Retiring one is {@code active: false}, which keeps it off the entry screen and leaves
	 * the statements readable — the same rule fee heads follow.
	 *
	 * @throws ConflictException 409 when any entry or transfer refers to it
	 */
	public void delete(String id) {
		MoneyAccount account = get(id);
		if (entries.existsByMoneyAccountId(id) || transfers.existsByFromAccountIdOrToAccountId(id, id)) {
			throw new ConflictException("Money is booked to " + account.getName()
					+ ", so it cannot be deleted. Set active=false to retire it instead.");
		}
		accounts.delete(account);
		audit.record(AuditAction.MONEY_ACCOUNT_DELETED, AUDIT_ENTITY, id, account, null);
	}

	// --- internals --------------------------------------------------------------------------------

	/** The bank fields are cleared on a CASH account rather than rejected, as the employee halves are. */
	private MoneyAccount.MoneyAccountBuilder apply(MoneyAccount.MoneyAccountBuilder builder,
			MoneyAccountRequest request) {
		boolean bank = request.type() == MoneyAccountType.BANK;
		return builder
				.type(request.type())
				.bankName(bank ? trimToNull(request.bankName()) : null)
				.accountNumberLast4(bank ? trimToNull(request.accountNumberLast4()) : null)
				.openingBalance(request.openingBalance() == null ? 0L : request.openingBalance())
				.active(request.active() == null || request.active());
	}

	private MoneyAccount insert(MoneyAccount account) {
		try {
			return accounts.insert(account);
		}
		catch (DuplicateKeyException ex) {
			throw new ConflictException("A money account named " + account.getName() + " already exists", ex);
		}
	}

	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

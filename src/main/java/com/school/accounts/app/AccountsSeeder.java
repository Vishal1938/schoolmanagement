package com.school.accounts.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.school.accounts.domain.AccountCategory;
import com.school.accounts.domain.EntryType;
import com.school.accounts.domain.MoneyAccount;
import com.school.accounts.domain.MoneyAccountType;
import com.school.accounts.domain.SystemCategory;
import com.school.accounts.infra.AccountCategoryRepository;
import com.school.accounts.infra.MoneyAccountRepository;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Puts the starting money account and category list in place, <strong>on first use rather than at
 * boot</strong>.
 *
 * <p>On first use because the books are an optional module: a deployment that switches
 * {@code modules.accounts} off should not have two collections appear in its database, and a
 * deployment that switches it on a year later should get the list then. So this runs when somebody
 * first opens the accounts screens, not from an {@code ApplicationRunner}.
 *
 * <p>Idempotent twice over. The in-memory flag stops the two {@code count} queries running on every
 * request after the first, and the unique indexes — on a category's (type, name) and on its
 * {@code systemKey} — stop two instances that start together from both inserting. A duplicate key is
 * therefore swallowed: it means somebody else got there first, which is success.
 *
 * <p>The lists are a <em>starting point</em>, not a vocabulary the code depends on. Nothing anywhere
 * looks a category up by name except the three {@link SystemCategory} ones, which are found by
 * {@code systemKey}; everything here can be renamed, retired or added to (CLAUDE.md rule 6).
 */
@Component
public class AccountsSeeder {

	private static final Logger log = LoggerFactory.getLogger(AccountsSeeder.class);

	/** The one money account every school has. Opening balance zero: nobody knows what was in it. */
	private static final String CASH_ACCOUNT_NAME = "Cash in hand";

	/** What a school actually spends on, in the order an office would expect to see them. */
	private static final List<String> EXPENSE_CATEGORIES = List.of(
			"Electricity",
			"Water",
			"Rent",
			"Building maintenance",
			"Repairs",
			"Stationery & printing",
			"Furniture & equipment",
			"Exam expenses",
			"University/affiliation fees",
			"Events & functions",
			"Travel",
			"Internet & phone",
			"Cleaning & sanitation",
			"Bank charges",
			"Miscellaneous");

	/** Income that is not a student's fees — fees have their own module and their own system category. */
	private static final List<String> INCOME_CATEGORIES = List.of(
			"Donation",
			"Government grant",
			"Hall/ground rent",
			"Scrap sale",
			"Interest",
			"Miscellaneous income");

	private final MoneyAccountRepository accounts;
	private final AccountCategoryRepository categories;
	private final AuditService audit;
	private final Clock clock;

	/**
	 * Set once seeding has been found done or has been done. Only ever moves false → true, so a stale
	 * read costs one extra pair of counts and nothing else; it does not need to be synchronized.
	 */
	private volatile boolean seeded;

	public AccountsSeeder(MoneyAccountRepository accounts, AccountCategoryRepository categories, AuditService audit,
			Clock clock) {
		this.accounts = accounts;
		this.categories = categories;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Called at the start of every accounts read and write. Does nothing at all after the first time.
	 *
	 * <p>The money account and the categories are checked separately: a school that deleted the
	 * seeded cash account because it only banks should not have it come back, and one that had the
	 * module off when the category list grew should still get the categories.
	 */
	public void ensureSeeded() {
		if (seeded) {
			return;
		}
		int created = seedMoneyAccount() + seedCategories();
		if (created > 0) {
			// Actor is whoever first opened the screens, which is the honest answer: this is not a
			// startup task, and the entry says what appeared and when.
			audit.record(AuditAction.ACCOUNTS_SEEDED, "Accounts", "accounts",
					"Seeded " + created + " money accounts and categories on first use");
			log.info("Seeded the accounts module: {} documents written on first use", created);
		}
		seeded = true;
	}

	private int seedMoneyAccount() {
		if (accounts.count() > 0) {
			return 0;
		}
		Instant now = Instant.now(clock);
		try {
			accounts.insert(MoneyAccount.builder()
					.name(CASH_ACCOUNT_NAME)
					.type(MoneyAccountType.CASH)
					.openingBalance(0L)
					.openingDate(LocalDate.now(clock))
					.active(true)
					.createdAt(now)
					.updatedAt(now)
					.build());
			return 1;
		}
		catch (DuplicateKeyException ex) {
			// Another instance seeded it between the count and the insert. Nothing to do.
			return 0;
		}
	}

	private int seedCategories() {
		if (categories.count() > 0) {
			return 0;
		}
		int created = 0;
		for (String name : EXPENSE_CATEGORIES) {
			created += insert(name, EntryType.EXPENSE, null, false) ? 1 : 0;
		}
		for (String name : INCOME_CATEGORIES) {
			created += insert(name, EntryType.INCOME, null, false) ? 1 : 0;
		}
		for (SystemCategory system : SystemCategory.values()) {
			created += insert(system.displayName(), system.type(), system, system.asset()) ? 1 : 0;
		}
		return created;
	}

	private boolean insert(String name, EntryType type, SystemCategory systemKey, boolean asset) {
		Instant now = Instant.now(clock);
		try {
			categories.insert(AccountCategory.builder()
					.name(name)
					.type(type)
					.systemKey(systemKey)
					.asset(asset)
					.active(true)
					.createdAt(now)
					.updatedAt(now)
					.build());
			return true;
		}
		catch (DuplicateKeyException ex) {
			// Either another instance is seeding alongside us, or a school already has a category by
			// that name. Both mean the list is in place, which is what this method is for.
			return false;
		}
	}
}

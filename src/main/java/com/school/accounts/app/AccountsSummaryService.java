package com.school.accounts.app;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.school.accounts.api.AccountsSummaryResponse;
import com.school.accounts.domain.AccountCategory;
import com.school.accounts.domain.AccountEntry;
import com.school.accounts.domain.EntryStatus;
import com.school.accounts.domain.EntryType;
import com.school.accounts.domain.MoneyAccount;
import com.school.accounts.infra.AccountEntryRepository;
import org.springframework.stereotype.Service;

/**
 * What the books add up to over a period: income, expense, the balance of each money account, and
 * what is still payable.
 *
 * <p><strong>Asset categories are in neither total.</strong> An advance handed to an employee has
 * left a drawer but is still the school's money, and the installment that comes back is not
 * earnings; counting either would make a month with three advances look like a month with a
 * disaster. They are reported on their own line instead, net — given less recovered — which is what
 * the school is owed by its staff.
 *
 * <p>Only {@code PAID} entries count. An unpaid bill has moved no money, so it belongs under
 * {@code payable} rather than in the expense total, where it would be double-counted the day it is
 * settled.
 */
@Service
public class AccountsSummaryService {

	private final AccountEntryRepository entries;
	private final AccountCategoryService categories;
	private final MoneyAccountService accounts;
	private final AccountsSeeder seeder;
	private final Clock clock;

	public AccountsSummaryService(AccountEntryRepository entries, AccountCategoryService categories,
			MoneyAccountService accounts, AccountsSeeder seeder, Clock clock) {
		this.entries = entries;
		this.categories = categories;
		this.accounts = accounts;
		this.seeder = seeder;
		this.clock = clock;
	}

	/**
	 * The summary for a closed date range, both ends inclusive.
	 *
	 * <p>Defaults to the current calendar month, which is what an office opening the screen wants.
	 * Balances are <em>as of now</em> rather than as of the end of the range: "what is in the drawer"
	 * has one answer, and it is not a function of which month is being looked at.
	 */
	public AccountsSummaryResponse summary(LocalDate from, LocalDate to) {
		seeder.ensureSeeded();
		LocalDate today = LocalDate.now(clock);
		LocalDate start = from == null ? today.withDayOfMonth(1) : from;
		LocalDate end = to == null ? today.withDayOfMonth(today.lengthOfMonth()) : to;

		List<AccountEntry> settled = entries.findSettledBetween(start, end, EntryStatus.PAID);
		Map<String, AccountCategory> byId = categories.byId(
				settled.stream().map(AccountEntry::getCategoryId).distinct().toList());

		long income = 0L;
		long expense = 0L;
		long advancesGiven = 0L;
		long advancesRecovered = 0L;
		Map<String, Long> byCategory = new LinkedHashMap<>();

		for (AccountEntry entry : settled) {
			AccountCategory category = byId.get(entry.getCategoryId());
			boolean asset = category != null && category.isAsset();
			if (asset) {
				// The school's own money moving. Neither total, and tracked on its own line.
				if (entry.getType() == EntryType.EXPENSE) {
					advancesGiven += entry.getAmount();
				}
				else {
					advancesRecovered += entry.getAmount();
				}
				continue;
			}
			if (entry.getType() == EntryType.INCOME) {
				income += entry.getAmount();
			}
			else {
				expense += entry.getAmount();
			}
			String name = category == null ? "(deleted category)" : category.getName();
			byCategory.merge(name, entry.getAmount(), Long::sum);
		}

		List<AccountsSummaryResponse.CategoryTotal> totals = new ArrayList<>();
		byCategory.forEach((name, amount) -> totals.add(new AccountsSummaryResponse.CategoryTotal(name, amount)));
		// Biggest first: the question an expense report is opened to answer is "what did we spend most
		// on", and a list by name makes that a reading exercise.
		totals.sort(Comparator.comparingLong(AccountsSummaryResponse.CategoryTotal::amount).reversed());

		List<AccountsSummaryResponse.AccountBalance> balances = new ArrayList<>();
		for (MoneyAccount account : accounts.list(null)) {
			balances.add(new AccountsSummaryResponse.AccountBalance(account.getId(), account.getName(),
					account.getType(), account.isActive(), accounts.balanceOf(account.getId())));
		}

		// Everything still owed, whatever month is being looked at: a bill falling due next week is
		// not a figure that belongs to the range somebody happens to be reading.
		List<AccountEntry> unpaid = entries.findByStatusOrderByDueDateAsc(EntryStatus.UNPAID);
		long payable = unpaid.stream().mapToLong(AccountEntry::getAmount).sum();
		long overdue = unpaid.stream()
				.filter(entry -> entry.getDueDate() != null && entry.getDueDate().isBefore(today))
				.mapToLong(AccountEntry::getAmount)
				.sum();

		return new AccountsSummaryResponse(start, end, income, expense, income - expense,
				advancesGiven, advancesRecovered, advancesGiven - advancesRecovered, payable, overdue,
				totals, balances);
	}
}

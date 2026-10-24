package com.school.accounts.infra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.school.accounts.domain.AccountEntry;
import com.school.accounts.domain.EntryStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

/**
 * Repository for {@code account_entries}. Internal to this module; callers use
 * {@code AccountEntryService}.
 *
 * <p>The filtered list is not here: it is built with a {@code Criteria} chain in the service,
 * because eight optional filters cannot be expressed as a derived method name.
 */
public interface AccountEntryRepository extends MongoRepository<AccountEntry, String> {

	/** Whether an automatic entry for this source already exists; the idempotency check. */
	Optional<AccountEntry> findBySourceRef(String sourceRef);

	/** Whether anything at all is booked to a money account, before it may be deleted. */
	boolean existsByMoneyAccountId(String moneyAccountId);

	/** Whether anything at all names a category, before it may be deleted. */
	boolean existsByCategoryId(String categoryId);

	/**
	 * Every settled entry on one money account, for its balance. {@code UNPAID} bills are left out on
	 * purpose: a bill the school has not paid has not moved any money yet.
	 *
	 * <p>Written out rather than derived because the status filter and the account filter together are
	 * what {@code account_entries_money_account_date_idx} serves.
	 */
	@Query("{ 'moneyAccountId': ?0, 'status': ?1 }")
	List<AccountEntry> findSettledForAccount(String moneyAccountId, EntryStatus status);

	/** Settled entries in a closed date range, for the summary. Both ends inclusive. */
	@Query(value = "{ 'status': ?2, 'date': { '$gte': ?0, '$lte': ?1 } }", sort = "{ 'date': 1 }")
	List<AccountEntry> findSettledBetween(LocalDate from, LocalDate to, EntryStatus status);

	/**
	 * Bills payable, soonest due first — the summary's {@code payable} figure and the list an office
	 * works from. Not date-ranged: what is owed is owed whatever month is being looked at.
	 */
	List<AccountEntry> findByStatusOrderByDueDateAsc(EntryStatus status);
}

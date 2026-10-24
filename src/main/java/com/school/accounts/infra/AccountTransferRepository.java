package com.school.accounts.infra;

import java.time.LocalDate;
import java.util.List;

import com.school.accounts.domain.AccountTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

/**
 * Repository for {@code account_transfers}. Internal to this module; callers use
 * {@code AccountTransferService}.
 */
public interface AccountTransferRepository extends MongoRepository<AccountTransfer, String> {

	Page<AccountTransfer> findAllByOrderByDateDesc(Pageable pageable);

	/** Transfers in a closed date range, newest first — the list screen's only filter. */
	@Query(value = "{ 'date': { '$gte': ?0, '$lte': ?1 } }", sort = "{ 'date': -1 }")
	Page<AccountTransfer> findBetween(LocalDate from, LocalDate to, Pageable pageable);

	/** What left an account, for its balance. */
	List<AccountTransfer> findByFromAccountId(String fromAccountId);

	/** What arrived in an account, for its balance. */
	List<AccountTransfer> findByToAccountId(String toAccountId);

	boolean existsByFromAccountIdOrToAccountId(String fromAccountId, String toAccountId);
}

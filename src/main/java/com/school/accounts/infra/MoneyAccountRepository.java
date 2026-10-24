package com.school.accounts.infra;

import java.util.List;

import com.school.accounts.domain.MoneyAccount;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Repository for {@code money_accounts}. Internal to this module; callers use {@code MoneyAccountService}. */
public interface MoneyAccountRepository extends MongoRepository<MoneyAccount, String> {

	List<MoneyAccount> findAllByOrderByNameAsc();

	List<MoneyAccount> findByActiveOrderByNameAsc(boolean active);

	boolean existsByName(String name);

	boolean existsByNameAndIdNot(String name, String id);
}

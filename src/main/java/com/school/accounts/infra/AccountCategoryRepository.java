package com.school.accounts.infra;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.school.accounts.domain.AccountCategory;
import com.school.accounts.domain.EntryType;
import com.school.accounts.domain.SystemCategory;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository for {@code account_categories}. Internal to this module; callers use
 * {@code AccountCategoryService}.
 */
public interface AccountCategoryRepository extends MongoRepository<AccountCategory, String> {

	List<AccountCategory> findAllByOrderByTypeAscNameAsc();

	List<AccountCategory> findByTypeOrderByNameAsc(EntryType type);

	List<AccountCategory> findByActiveOrderByTypeAscNameAsc(boolean active);

	List<AccountCategory> findByTypeAndActiveOrderByNameAsc(EntryType type, boolean active);

	/** How the automatic writers find the category they book against. */
	Optional<AccountCategory> findBySystemKey(SystemCategory systemKey);

	/** Resolving the names a page of entries needs, in one read rather than one per row. */
	List<AccountCategory> findAllByIdIn(Collection<String> ids);

	boolean existsByTypeAndName(EntryType type, String name);

	boolean existsByTypeAndNameAndIdNot(EntryType type, String name, String id);
}

package com.school.accounts.app;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.school.accounts.api.AccountCategoryRequest;
import com.school.accounts.domain.AccountCategory;
import com.school.accounts.domain.EntryType;
import com.school.accounts.domain.SystemCategory;
import com.school.accounts.infra.AccountCategoryRepository;
import com.school.accounts.infra.AccountEntryRepository;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.BusinessRuleException;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.NotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * What a line in the books is for.
 *
 * <p>Two rules decide the shape of this class, and both are about the three
 * {@link SystemCategory} ones. They are <strong>not editable</strong>, because an administrator
 * renaming "Fees collected" or retiring "Salaries" would change where the automatic writers put
 * money or stop them finding a category at all. And they are <strong>not selectable</strong> for a
 * manual entry: the way to record a fee collection is to take a payment, and a hand-written line in
 * that category would double-count the month.
 */
@Service
public class AccountCategoryService {

	static final String AUDIT_ENTITY = "AccountCategory";

	private final AccountCategoryRepository categories;
	private final AccountEntryRepository entries;
	private final AccountsSeeder seeder;
	private final AuditService audit;
	private final Clock clock;

	public AccountCategoryService(AccountCategoryRepository categories, AccountEntryRepository entries,
			AccountsSeeder seeder, AuditService audit, Clock clock) {
		this.categories = categories;
		this.entries = entries;
		this.seeder = seeder;
		this.audit = audit;
		this.clock = clock;
	}

	// --- reading ----------------------------------------------------------------------------------

	/** The category list, optionally narrowed to one side of the books or to the active ones. */
	public List<AccountCategory> list(EntryType type, Boolean active) {
		seeder.ensureSeeded();
		if (type != null && active != null) {
			return categories.findByTypeAndActiveOrderByNameAsc(type, active);
		}
		if (type != null) {
			return categories.findByTypeOrderByNameAsc(type);
		}
		if (active != null) {
			return categories.findByActiveOrderByTypeAscNameAsc(active);
		}
		return categories.findAllByOrderByTypeAscNameAsc();
	}

	public AccountCategory get(String id) {
		return categories.findById(id).orElseThrow(() -> NotFoundException.of("Category", id));
	}

	/** Category id to name for a page of entries, in one read. Absent ids are simply left out. */
	Map<String, AccountCategory> byId(Collection<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return Map.of();
		}
		Map<String, AccountCategory> found = new LinkedHashMap<>();
		categories.findAllByIdIn(ids).forEach(category -> found.put(category.getId(), category));
		return found;
	}

	/**
	 * The category one of the automatic writers books against, seeding the list if this is the first
	 * thing the module has done.
	 *
	 * @throws NotFoundException when a school has deleted it out from under us, which the delete rules
	 *                          below make impossible through the API
	 */
	AccountCategory requireSystem(SystemCategory systemKey) {
		seeder.ensureSeeded();
		return categories.findBySystemKey(systemKey)
				.orElseThrow(() -> new NotFoundException("The " + systemKey.displayName()
						+ " category is missing from this deployment"));
	}

	/**
	 * The category a manual entry may be filed under.
	 *
	 * @throws BusinessRuleException 422 for a system category, a retired one, or one on the wrong side
	 *                               of the books
	 */
	AccountCategory requireSelectable(String id, EntryType type) {
		AccountCategory category = get(id);
		if (category.isSystem()) {
			throw new BusinessRuleException(category.getName()
					+ " is written by the application itself and cannot be chosen for an entry. A fee "
					+ "collection is recorded as a payment and a salary through payroll.");
		}
		if (!category.isActive()) {
			throw new BusinessRuleException(category.getName() + " has been retired and cannot be used "
					+ "for new entries.");
		}
		if (category.getType() != type) {
			throw new BusinessRuleException(category.getName() + " is a " + category.getType()
					+ " category, so it cannot be used for an " + type + " entry.");
		}
		return category;
	}

	// --- writing ----------------------------------------------------------------------------------

	public AccountCategory create(AccountCategoryRequest request) {
		seeder.ensureSeeded();
		String name = request.name().trim();
		if (categories.existsByTypeAndName(request.type(), name)) {
			throw new ConflictException("A " + request.type() + " category named " + name + " already exists");
		}
		Instant now = Instant.now(clock);
		AccountCategory saved = insert(AccountCategory.builder()
				.name(name)
				.type(request.type())
				// Only the seeder makes system or asset categories; nothing a school adds is either.
				.systemKey(null)
				.asset(false)
				.active(request.active() == null || request.active())
				.createdAt(now)
				.updatedAt(now)
				.build());
		audit.record(AuditAction.ACCOUNT_CATEGORY_CREATED, AUDIT_ENTITY, saved.getId(), null, saved);
		return saved;
	}

	/**
	 * Renames a category or retires it. The type never moves: a year of entries is already filed on
	 * one side of the books, and flipping it would move all of them at once.
	 */
	public AccountCategory update(String id, AccountCategoryRequest request) {
		AccountCategory before = requireEditable(id);
		String name = request.name().trim();
		if (categories.existsByTypeAndNameAndIdNot(before.getType(), name, id)) {
			throw new ConflictException("Another " + before.getType() + " category is named " + name);
		}
		AccountCategory saved = categories.save(before.toBuilder()
				.name(name)
				.active(request.active() == null ? before.isActive() : request.active())
				.updatedAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.ACCOUNT_CATEGORY_UPDATED, AUDIT_ENTITY, id, before, saved);
		return saved;
	}

	/**
	 * Removes a category, but only while nothing has ever been filed under it — the same rule as a
	 * money account, and for the same reason: entries name their category, and a report cannot print a
	 * line whose category has gone. Retiring one is {@code active: false}.
	 *
	 * @throws ConflictException 409 when any entry names it
	 * @throws BusinessRuleException 422 for a system category
	 */
	public void delete(String id) {
		AccountCategory category = requireEditable(id);
		if (entries.existsByCategoryId(id)) {
			throw new ConflictException("Entries are filed under " + category.getName()
					+ ", so it cannot be deleted. Set active=false to retire it instead.");
		}
		categories.delete(category);
		audit.record(AuditAction.ACCOUNT_CATEGORY_DELETED, AUDIT_ENTITY, id, category, null);
	}

	// --- internals --------------------------------------------------------------------------------

	private AccountCategory requireEditable(String id) {
		AccountCategory category = get(id);
		if (category.isSystem()) {
			throw new BusinessRuleException(category.getName()
					+ " is one of the application's own categories and cannot be edited or deleted. The "
					+ "automatic entries are booked against it, so renaming or retiring it would break them.");
		}
		return category;
	}

	private AccountCategory insert(AccountCategory category) {
		try {
			return categories.insert(category);
		}
		catch (DuplicateKeyException ex) {
			throw new ConflictException("A " + category.getType() + " category named " + category.getName()
					+ " already exists", ex);
		}
	}
}

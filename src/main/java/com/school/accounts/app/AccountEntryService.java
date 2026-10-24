package com.school.accounts.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.school.accounts.api.AccountEntryRequest;
import com.school.accounts.api.PayEntryRequest;
import com.school.accounts.domain.AccountCategory;
import com.school.accounts.domain.AccountEntry;
import com.school.accounts.domain.EntrySource;
import com.school.accounts.domain.EntryStatus;
import com.school.accounts.domain.EntryType;
import com.school.accounts.domain.MoneyAccount;
import com.school.accounts.infra.AccountEntryRepository;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.BusinessRuleException;
import com.school.common.exceptions.ConflictException;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.NotFoundException;
import com.school.common.exceptions.ValidationException;
import com.school.common.security.AuthPrincipal;
import com.school.common.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * The day book: reading the entries, writing the ones somebody types in, and settling a bill.
 *
 * <p>Three rules run through all of it.
 *
 * <p><strong>Only manual entries are writable here.</strong> A line written from a fee payment, a
 * salary or an advance is the shadow of a fact recorded elsewhere, and the way to change one is to
 * change what it came from. Editing the shadow would leave the books and the fee register disagreeing
 * with nothing to say which is right, so {@code PUT} and {@code DELETE} answer 422 for one.
 *
 * <p><strong>A closed period is closed.</strong> Every write goes through
 * {@link AccountSettingsService#requireUnlocked}, with both the old and the new date on an edit —
 * moving a line out of a locked month is as much a rewrite of it as moving one in.
 *
 * <p><strong>{@code UNPAID} is for expenses only.</strong> It is a bill received and not yet
 * settled: no money account and no {@code paidOn} until {@link #pay} supplies them. Money the school
 * is <em>owed</em> is a fee invoice and belongs to the fees module; a second home for receivables
 * would mean two answers to the same question.
 */
@Service
public class AccountEntryService {

	static final String AUDIT_ENTITY = "AccountEntry";

	private final AccountEntryRepository entries;
	private final AccountCategoryService categories;
	private final MoneyAccountService accounts;
	private final AccountSettingsService settings;
	private final EntryAttachmentService attachments;
	private final AccountsSeeder seeder;
	private final MongoOperations mongo;
	private final AuditService audit;
	private final Clock clock;

	public AccountEntryService(AccountEntryRepository entries, AccountCategoryService categories,
			MoneyAccountService accounts, AccountSettingsService settings, EntryAttachmentService attachments,
			AccountsSeeder seeder, MongoOperations mongo, AuditService audit, Clock clock) {
		this.entries = entries;
		this.categories = categories;
		this.accounts = accounts;
		this.settings = settings;
		this.attachments = attachments;
		this.seeder = seeder;
		this.mongo = mongo;
		this.audit = audit;
		this.clock = clock;
	}

	// --- reading ----------------------------------------------------------------------------------

	/**
	 * The filtered, paged day book, newest first.
	 *
	 * <p>Built as a {@code Criteria} chain rather than a derived repository method because eight
	 * optional filters cannot be a method name. {@code q} is an anchored prefix against the party and
	 * the bill number, quoted before it reaches the regex so a caller cannot inject one — the same
	 * shape as the employee search.
	 */
	public Page<AccountEntry> search(EntrySearch search, Pageable pageable) {
		seeder.ensureSeeded();
		Query query = new Query();
		List<Criteria> criteria = new ArrayList<>();
		// Both ends on one Criteria, chained rather than added twice: two clauses on "date" would be
		// the same key twice in one BSON document, which is the InvalidMongoDbApiUsageException the
		// collection report's @Query exists to avoid.
		if (search.from() != null || search.to() != null) {
			Criteria date = Criteria.where("date");
			if (search.from() != null) {
				date = date.gte(search.from());
			}
			if (search.to() != null) {
				date = date.lte(search.to());
			}
			criteria.add(date);
		}
		if (search.type() != null) {
			criteria.add(Criteria.where("type").is(search.type()));
		}
		if (hasText(search.category())) {
			criteria.add(Criteria.where("categoryId").is(search.category().trim()));
		}
		if (hasText(search.account())) {
			criteria.add(Criteria.where("moneyAccountId").is(search.account().trim()));
		}
		if (search.status() != null) {
			criteria.add(Criteria.where("status").is(search.status()));
		}
		if (search.source() != null) {
			criteria.add(Criteria.where("source").is(search.source()));
		}
		if (hasText(search.q())) {
			String prefix = "^" + Pattern.quote(search.q().trim());
			criteria.add(new Criteria().orOperator(
					Criteria.where("party").regex(prefix, "i"),
					Criteria.where("billNo").regex(prefix, "i")));
		}
		if (!criteria.isEmpty()) {
			query.addCriteria(new Criteria().andOperator(criteria));
		}

		long total = mongo.count(query, AccountEntry.class);
		// Newest first unless the caller asked otherwise; a day book read oldest-first is nobody's
		// default. Served by account_entries_date_idx.
		Pageable effective = pageable.getSort().isSorted()
				? pageable
				: PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "date"));
		return new PageImpl<>(mongo.find(query.with(effective), AccountEntry.class), effective, total);
	}

	public AccountEntry get(String id) {
		return entries.findById(id).orElseThrow(() -> NotFoundException.of("Entry", id));
	}

	/** The categories a page of entries names, so the response can carry names rather than ids only. */
	public Map<String, AccountCategory> categoriesOf(List<AccountEntry> page) {
		return categories.byId(page.stream().map(AccountEntry::getCategoryId).filter(Objects::nonNull)
				.distinct().toList());
	}

	/** The money accounts a page of entries names, as id to name. */
	public Map<String, String> accountNamesOf(List<AccountEntry> page) {
		return accounts.namesOf(page.stream().map(AccountEntry::getMoneyAccountId)
				.filter(Objects::nonNull).distinct().toList());
	}

	// --- writing ----------------------------------------------------------------------------------

	/** Records a line somebody typed in. */
	public AccountEntry create(AccountEntryRequest request) {
		seeder.ensureSeeded();
		EntryStatus status = request.statusOrDefault();
		validate(request, status);
		settings.requireUnlocked(request.date());
		AccountCategory category = categories.requireSelectable(request.categoryId(), request.type());
		String moneyAccountId = status == EntryStatus.PAID ? requireUsableAccount(request.moneyAccountId()) : null;
		attachments.requireOwnUrls(request.attachments());

		Instant now = Instant.now(clock);
		AccountEntry saved = entries.insert(AccountEntry.builder()
				.type(request.type())
				.date(request.date())
				.categoryId(category.getId())
				.amount(request.amount())
				.party(trimToNull(request.party()))
				.description(trimToNull(request.description()))
				.moneyAccountId(moneyAccountId)
				.mode(status == EntryStatus.PAID ? request.mode() : null)
				.reference(status == EntryStatus.PAID ? trimToNull(request.reference()) : null)
				.billNo(trimToNull(request.billNo()))
				.attachments(request.attachments() == null ? List.of() : List.copyOf(request.attachments()))
				.status(status)
				.dueDate(status == EntryStatus.UNPAID ? request.dueDate() : null)
				// An entry recorded as paid was paid on its own date unless told otherwise, which is what
				// "I am entering today's electricity bill" means.
				.paidOn(status == EntryStatus.PAID
						? (request.paidOn() == null ? request.date() : request.paidOn())
						: null)
				.recordedBy(currentUniqueId())
				.source(EntrySource.MANUAL)
				.createdAt(now)
				.updatedAt(now)
				.build());
		audit.record(AuditAction.ACCOUNT_ENTRY_CREATED, AUDIT_ENTITY, saved.getId(), null, saved);
		return saved;
	}

	/**
	 * Replaces a manual entry. The whole line is sent, as everywhere else in this API.
	 *
	 * <p>An entry may move between {@code PAID} and {@code UNPAID} here — a bill entered as paid by
	 * mistake is the commonest correction there is — and both dates are checked against the lock.
	 */
	public AccountEntry update(String id, AccountEntryRequest request) {
		AccountEntry before = requireManual(id);
		EntryStatus status = request.statusOrDefault();
		validate(request, status);
		settings.requireUnlocked(before.getDate(), request.date());
		AccountCategory category = categories.requireSelectable(request.categoryId(), request.type());
		String moneyAccountId = status == EntryStatus.PAID ? requireUsableAccount(request.moneyAccountId()) : null;
		attachments.requireOwnUrls(request.attachments());

		AccountEntry saved = entries.save(before.toBuilder()
				.type(request.type())
				.date(request.date())
				.categoryId(category.getId())
				.amount(request.amount())
				.party(trimToNull(request.party()))
				.description(trimToNull(request.description()))
				.moneyAccountId(moneyAccountId)
				.mode(status == EntryStatus.PAID ? request.mode() : null)
				.reference(status == EntryStatus.PAID ? trimToNull(request.reference()) : null)
				.billNo(trimToNull(request.billNo()))
				.attachments(request.attachments() == null ? List.of() : List.copyOf(request.attachments()))
				.status(status)
				.dueDate(status == EntryStatus.UNPAID ? request.dueDate() : null)
				.paidOn(status == EntryStatus.PAID
						? (request.paidOn() == null ? request.date() : request.paidOn())
						: null)
				.updatedAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.ACCOUNT_ENTRY_UPDATED, AUDIT_ENTITY, id, before, saved);
		return saved;
	}

	/** Removes a manual entry. {@code before} keeps it in the trail, so a mistake is recoverable. */
	public void delete(String id) {
		AccountEntry entry = requireManual(id);
		settings.requireUnlocked(entry.getDate());
		entries.delete(entry);
		audit.record(AuditAction.ACCOUNT_ENTRY_DELETED, AUDIT_ENTITY, id, entry, null);
	}

	/**
	 * Settles a bill payable: the money account it came out of, the mode, the reference and the day.
	 *
	 * <p>A separate endpoint from {@code PUT} because this is the one thing about an unpaid bill that
	 * happens later and on its own, and because it is the moment a money account's balance moves —
	 * which is worth its own entry in the trail rather than being one of a dozen fields in an update.
	 *
	 * @throws ConflictException 409 if it has already been paid
	 */
	public AccountEntry pay(String id, PayEntryRequest request) {
		AccountEntry before = get(id);
		if (before.getStatus() != EntryStatus.UNPAID) {
			throw new ConflictException("This entry was already paid on " + before.getPaidOn()
					+ ", so it cannot be paid again.");
		}
		LocalDate paidOn = request.paidOn() == null ? LocalDate.now(clock) : request.paidOn();
		if (paidOn.isAfter(LocalDate.now(clock))) {
			throw new ValidationException("The payment date is in the future",
					List.of(new FieldViolation("paidOn", "a bill is recorded as paid when the money goes out")));
		}
		// Both dates: the bill's own date decides which period it belongs to, and the payment date
		// decides which period the money moved in.
		settings.requireUnlocked(before.getDate(), paidOn);
		String moneyAccountId = requireUsableAccount(request.moneyAccountId());

		AccountEntry saved = entries.save(before.toBuilder()
				.status(EntryStatus.PAID)
				.moneyAccountId(moneyAccountId)
				.mode(request.mode())
				.reference(trimToNull(request.reference()))
				.paidOn(paidOn)
				.dueDate(null)
				.updatedAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.ACCOUNT_ENTRY_PAID, AUDIT_ENTITY, id, before, saved);
		return saved;
	}

	// --- validation -------------------------------------------------------------------------------

	/**
	 * The rules Bean Validation cannot express, because each depends on another field: which fields a
	 * status needs, and that only an expense can be unpaid.
	 */
	private void validate(AccountEntryRequest request, EntryStatus status) {
		List<FieldViolation> violations = new ArrayList<>();
		if (request.date().isAfter(LocalDate.now(clock))) {
			violations.add(new FieldViolation("date", "is in the future; the books record what has happened"));
		}
		if (status == EntryStatus.UNPAID) {
			if (request.type() != EntryType.EXPENSE) {
				violations.add(new FieldViolation("status",
						"UNPAID is only for an expense — a bill the school has received and not yet paid. "
								+ "Money owed to the school is a fee invoice."));
			}
			if (request.dueDate() == null) {
				violations.add(new FieldViolation("dueDate", "is required for an unpaid bill"));
			}
		}
		else {
			if (request.moneyAccountId() == null || request.moneyAccountId().isBlank()) {
				violations.add(new FieldViolation("moneyAccountId",
						"is required for a paid entry — the money came out of, or went into, somewhere"));
			}
			if (request.mode() == null) {
				violations.add(new FieldViolation("mode", "is required for a paid entry"));
			}
			if (request.paidOn() != null && request.paidOn().isAfter(LocalDate.now(clock))) {
				violations.add(new FieldViolation("paidOn", "is in the future"));
			}
		}
		if (!violations.isEmpty()) {
			throw new ValidationException("The entry could not be saved", violations);
		}
	}

	private AccountEntry requireManual(String id) {
		AccountEntry entry = get(id);
		if (!entry.isManual()) {
			throw new BusinessRuleException("This entry was written from a " + entry.getSource()
					+ " record and is read-only here. Change it where it was recorded — in fees for a "
					+ "collection, in payroll for a salary or an advance — and the books follow.");
		}
		return entry;
	}

	/** A money account that exists and has not been retired. */
	private String requireUsableAccount(String id) {
		MoneyAccount account = accounts.get(id);
		if (!account.isActive()) {
			throw new BusinessRuleException(account.getName()
					+ " has been retired, so nothing new can be booked to it.");
		}
		return account.getId();
	}

	// --- internals --------------------------------------------------------------------------------

	private static String currentUniqueId() {
		return CurrentUser.principal().map(AuthPrincipal::uniqueId).orElse(null);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

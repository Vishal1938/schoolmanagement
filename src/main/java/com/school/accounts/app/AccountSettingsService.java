package com.school.accounts.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.school.accounts.api.AccountSettingsRequest;
import com.school.accounts.domain.AccountDefaults;
import com.school.accounts.domain.AccountSettings;
import com.school.accounts.domain.MoneyAccount;
import com.school.accounts.infra.AccountSettingsRepository;
import com.school.common.audit.AuditAction;
import com.school.common.audit.AuditService;
import com.school.common.exceptions.BusinessRuleException;
import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ValidationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * The expense book's own settings: which money account each kind of automatic entry lands in, and
 * how far back the books are closed.
 *
 * <p>Also the one place the <strong>month lock</strong> is enforced. Every write in this module —
 * an entry, a bill payment, a transfer — calls {@link #requireUnlocked} first, so there is a single
 * definition of "that period is closed" rather than a date comparison in six services.
 *
 * <p><strong>The automatic entries are not held to the lock.</strong> A fee payment taken today
 * against a locked month, or a salary backdated into one, has already happened: the money moved, and
 * refusing to book it would either roll back the payment or leave the books quietly short. The lock
 * exists to stop a <em>person</em> rewriting a closed period, which is what the request-driven paths
 * do.
 */
@Service
public class AccountSettingsService {

	static final String AUDIT_ENTITY = "AccountSettings";

	private final AccountSettingsRepository settings;
	private final MoneyAccountService accounts;
	private final AccountsSeeder seeder;
	private final AuditService audit;
	private final Clock clock;

	public AccountSettingsService(AccountSettingsRepository settings, MoneyAccountService accounts,
			AccountsSeeder seeder, AuditService audit, Clock clock) {
		this.settings = settings;
		this.accounts = accounts;
		this.seeder = seeder;
		this.audit = audit;
		this.clock = clock;
	}

	// --- reading ----------------------------------------------------------------------------------

	/**
	 * The settings, creating the empty document on first read.
	 *
	 * <p>Created rather than returned as null so that {@code @Version} has something to guard and the
	 * first {@code PUT} is an update like every other one. The insert carries the fixed id, so two
	 * instances cannot both win.
	 */
	public AccountSettings get() {
		seeder.ensureSeeded();
		return settings.findById(AccountSettings.SINGLETON_ID).orElseGet(this::insertEmpty);
	}

	/** Where the automatic entries go. Never null; unset fields are null inside it. */
	public AccountDefaults defaults() {
		return get().resolvedDefaults();
	}

	// --- the defaults -----------------------------------------------------------------------------

	/**
	 * Replaces the default money accounts.
	 *
	 * <p>Each id is checked to be a money account that exists, because a typo here would silently send
	 * every future fee collection to an account nobody looks at. Null is allowed and means "not
	 * configured": automatic entries are then written without a money account rather than refused, so
	 * a school that has not got round to this does not lose its books.
	 */
	public AccountSettings updateDefaults(AccountSettingsRequest request) {
		AccountSettings before = get();
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put("cashFeesTo", request.cashFeesTo());
		fields.put("bankFeesTo", request.bankFeesTo());
		fields.put("salariesFrom", request.salariesFrom());
		requireMoneyAccounts(fields);

		AccountSettings saved = settings.save(before.toBuilder()
				.defaults(new AccountDefaults(trimToNull(request.cashFeesTo()), trimToNull(request.bankFeesTo()),
						trimToNull(request.salariesFrom())))
				.updatedAt(Instant.now(clock))
				.build());
		audit.record(AuditAction.ACCOUNT_SETTINGS_UPDATED, AUDIT_ENTITY, saved.getId(), before, saved);
		return saved;
	}

	// --- the month lock ---------------------------------------------------------------------------

	/**
	 * Closes the books on and before {@code upTo}, or re-opens them when it is null.
	 *
	 * <p>Both directions are audited, and the unlock deliberately as loudly as the lock: re-opening a
	 * closed month is the thing somebody would want to find in the trail afterwards. Moving the lock
	 * backwards is allowed — it is the same act as unlocking, just partial — and is the same entry.
	 *
	 * @param upTo the last closed day, or null to re-open everything
	 */
	public AccountSettings lock(LocalDate upTo) {
		AccountSettings before = get();
		if (upTo != null && upTo.isAfter(LocalDate.now(clock))) {
			throw new ValidationException("The books cannot be closed into the future",
					List.of(new FieldViolation("upTo", "is after today; a period is closed once it is over")));
		}
		AccountSettings saved = settings.save(before.toBuilder()
				.lockedUpTo(upTo)
				.updatedAt(Instant.now(clock))
				.build());
		boolean reopening = upTo == null
				|| (before.getLockedUpTo() != null && upTo.isBefore(before.getLockedUpTo()));
		audit.record(reopening ? AuditAction.ACCOUNTS_UNLOCKED : AuditAction.ACCOUNTS_LOCKED,
				AUDIT_ENTITY, saved.getId(), before, saved);
		return saved;
	}

	/** The last closed day, or null when nothing is closed. */
	public LocalDate lockedUpTo() {
		return get().getLockedUpTo();
	}

	/**
	 * The guard every request-driven write in this module calls before touching a dated row.
	 *
	 * <p>Called with <em>both</em> dates on a move — the date an entry had and the date it is being
	 * given — because editing a line out of a closed month and editing one into it are both rewriting
	 * a closed period.
	 *
	 * @throws BusinessRuleException 422 when the date falls on or before the lock
	 */
	public void requireUnlocked(LocalDate... dates) {
		LocalDate lockedUpTo = lockedUpTo();
		if (lockedUpTo == null) {
			return;
		}
		for (LocalDate date : dates) {
			if (date != null && !date.isAfter(lockedUpTo)) {
				throw new BusinessRuleException("The books are closed up to " + lockedUpTo
						+ ", so nothing dated " + date + " can be added, changed or removed. "
						+ "An administrator can re-open the period through POST /accounts/lock.");
			}
		}
	}

	// --- internals --------------------------------------------------------------------------------

	private AccountSettings insertEmpty() {
		Instant now = Instant.now(clock);
		try {
			return settings.insert(AccountSettings.builder()
					.id(AccountSettings.SINGLETON_ID)
					.defaults(AccountDefaults.NONE)
					.createdAt(now)
					.updatedAt(now)
					.build());
		}
		catch (DuplicateKeyException ex) {
			// Another request created it a moment ago; its version is the one to work from.
			return settings.findById(AccountSettings.SINGLETON_ID).orElseThrow(() -> ex);
		}
	}

	/** Every non-null id has to name a money account that exists. */
	private void requireMoneyAccounts(Map<String, String> byField) {
		List<FieldViolation> violations = new ArrayList<>();
		byField.forEach((field, id) -> {
			String trimmed = trimToNull(id);
			if (trimmed != null && accounts.find(trimmed).map(MoneyAccount::getId).isEmpty()) {
				violations.add(new FieldViolation(field, "is not a money account of this school"));
			}
		});
		if (!violations.isEmpty()) {
			throw new ValidationException("Those default accounts could not be set", violations);
		}
	}

	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}

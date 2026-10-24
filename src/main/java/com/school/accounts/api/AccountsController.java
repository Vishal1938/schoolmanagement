package com.school.accounts.api;

import java.time.LocalDate;

import com.school.accounts.app.AccountSettingsService;
import com.school.accounts.app.AccountsBackfillService;
import com.school.accounts.app.AccountsSummaryService;
import com.school.common.security.HasPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The expense book's settings, its summary, the month lock and the one-time backfill.
 *
 * <p>The last two are an administrator's and not an accountant's, which is what
 * {@link HasPermission#ACCOUNTS_ADMIN} says: an accountant keeps the books and must not be able to
 * close their own month or rebuild the automatic entries.
 */
@RestController
@RequestMapping("/accounts")
@Tag(name = "Accounts", description = "The expense book: money accounts, categories, entries and transfers")
public class AccountsController {

	private final AccountSettingsService settings;
	private final AccountsSummaryService summary;
	private final AccountsBackfillService backfill;

	public AccountsController(AccountSettingsService settings, AccountsSummaryService summary,
			AccountsBackfillService backfill) {
		this.settings = settings;
		this.summary = summary;
		this.backfill = backfill;
	}

	// --- settings ---------------------------------------------------------------------------------

	@GetMapping("/settings")
	@PreAuthorize(HasPermission.ACCOUNTS_READ_OR_MANAGE)
	@Operation(summary = "Where the automatic entries go, and how far back the books are closed",
			description = "`defaults` names the money accounts fee collections and salaries are booked to; "
					+ "any of them may be null, which means they have not been configured yet. `lockedUpTo` is "
					+ "the last closed day, or null when nothing is closed — a client should grey out dates on "
					+ "or before it rather than letting a save 422.")
	public AccountSettingsResponse settings() {
		return AccountSettingsResponse.of(settings.get());
	}

	@PutMapping("/settings")
	@PreAuthorize(HasPermission.ACCOUNTS_MANAGE)
	@Operation(summary = "Set the default money accounts",
			description = "`{cashFeesTo, bankFeesTo, salariesFrom}`, each a money account id. Wholesale "
					+ "replacement: omitting one clears it. 422 for an id that is not a money account. Null is "
					+ "allowed and means not configured — automatic entries are then written **without** a "
					+ "money account rather than refused, which is the only choice that cannot lose a line, and "
					+ "`POST /accounts/backfill` will not fix them afterwards (they already exist). Set these "
					+ "before taking the first payment.")
	public AccountSettingsResponse updateSettings(@Valid @RequestBody AccountSettingsRequest request) {
		return AccountSettingsResponse.of(settings.updateDefaults(request));
	}

	// --- the summary ------------------------------------------------------------------------------

	@GetMapping("/summary")
	@PreAuthorize(HasPermission.ACCOUNTS_READ_OR_MANAGE)
	@Operation(summary = "What the books add up to over a period",
			description = "Defaults to the current calendar month. All figures are paise. **Staff advances "
					+ "are in neither `income` nor `expense`** — money handed to an employee has left a drawer "
					+ "but is still the school's, and the installment that comes back is not earnings; they are "
					+ "reported as `advancesGiven`, `advancesRecovered` and `advancesOutstanding` instead. "
					+ "Only PAID entries count: an unpaid bill is under `payable`, with the part past its due "
					+ "date under `overdue`. `balances` are as of **now**, not as of the end of the range.")
	public AccountsSummaryResponse summary(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return summary.summary(from, to);
	}

	// --- the month lock ---------------------------------------------------------------------------

	@PostMapping("/lock")
	@PreAuthorize(HasPermission.ACCOUNTS_ADMIN)
	@Operation(summary = "Close the books up to a date, or re-open them",
			description = "ADMIN. `{upTo: \"2026-09-30\"}` closes everything on or before that day: no entry "
					+ "or transfer dated then can be added, edited, deleted or paid, and both the old and the "
					+ "new date are checked when a line is moved. **`upTo: null` re-opens everything**, and an "
					+ "earlier date re-opens the difference; both are audited as `ACCOUNTS_UNLOCKED`. 422 for a "
					+ "date in the future. The automatic entries are deliberately **not** held to the lock: a "
					+ "payment or a salary backdated into a closed month has already happened, and refusing to "
					+ "book it would leave the books short.")
	public AccountSettingsResponse lock(@Valid @RequestBody LockRequest request) {
		return AccountSettingsResponse.of(settings.lock(request.upTo()));
	}

	// --- the backfill -----------------------------------------------------------------------------

	@PostMapping("/backfill")
	@PreAuthorize(HasPermission.ACCOUNTS_ADMIN)
	@Operation(summary = "Book everything that happened before the books existed",
			description = "ADMIN. Walks every captured fee payment, every salary paid out and every advance "
					+ "and recovery, writing the entries each would have produced. **Safe to run more than "
					+ "once, and on a live deployment**: each automatic entry is keyed on what it came from, so "
					+ "a record already booked comes back under `skipped` and is left exactly as it is — "
					+ "including a money account somebody has since corrected by hand. Not one transaction: a "
					+ "year of a real school is thousands of entries, and a run that dies halfway is finished "
					+ "by the next one.")
	public BackfillResponse backfill() {
		return backfill.run();
	}
}

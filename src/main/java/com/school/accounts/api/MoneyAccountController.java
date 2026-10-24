package com.school.accounts.api;

import java.util.List;

import com.school.accounts.app.MoneyAccountService;
import com.school.common.security.HasPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Money accounts: the cash drawer and the bank accounts, with what is in each. */
@RestController
@RequestMapping("/accounts/money-accounts")
@Tag(name = "Accounts", description = "The expense book: money accounts, categories, entries and transfers")
public class MoneyAccountController {

	private final MoneyAccountService accounts;

	public MoneyAccountController(MoneyAccountService accounts) {
		this.accounts = accounts;
	}

	@GetMapping
	@PreAuthorize(HasPermission.ACCOUNTS_READ_OR_MANAGE)
	@Operation(summary = "List money accounts with their balances",
			description = "By name. Pass active=true for the ones money can still be booked to; omit it for "
					+ "every account, including retired ones that old entries still name. `balance` is the "
					+ "opening balance plus every settled entry and transfer since — computed, never stored, so "
					+ "it cannot drift. Unpaid bills are not in it: they have moved no money. "
					+ "Seeds \"Cash in hand\" on the first call this deployment makes.")
	public List<MoneyAccountResponse> list(@RequestParam(required = false) Boolean active) {
		return accounts.list(active).stream()
				.map(account -> MoneyAccountResponse.of(account, accounts.balanceOf(account.getId())))
				.toList();
	}

	@GetMapping("/{id}")
	@PreAuthorize(HasPermission.ACCOUNTS_READ_OR_MANAGE)
	@Operation(summary = "One money account with its balance")
	public MoneyAccountResponse get(@PathVariable String id) {
		return MoneyAccountResponse.of(accounts.get(id), accounts.balanceOf(id));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.ACCOUNTS_MANAGE)
	@Operation(summary = "Add a money account",
			description = "Names are unique → 409 otherwise. `bankName` and `accountNumberLast4` are ignored "
					+ "on a CASH account. `openingBalance` is paise, defaults to 0 and may be negative for an "
					+ "overdraft; `openingDate` defaults to today.")
	public MoneyAccountResponse create(@Valid @RequestBody MoneyAccountRequest request) {
		return MoneyAccountResponse.of(accounts.create(request));
	}

	@PutMapping("/{id}")
	@PreAuthorize(HasPermission.ACCOUNTS_MANAGE)
	@Operation(summary = "Replace a money account",
			description = "Including the opening balance, which stays editable on purpose: it is the one "
					+ "figure a school never has right on day one, and correcting it is how the computed "
					+ "balance comes to agree with the passbook. Every change is in the audit trail.")
	public MoneyAccountResponse update(@PathVariable String id, @Valid @RequestBody MoneyAccountRequest request) {
		return MoneyAccountResponse.of(accounts.update(id, request));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(HasPermission.ACCOUNTS_MANAGE)
	@Operation(summary = "Delete a money account",
			description = "Only while nothing has ever been booked to it → 409 otherwise. An account with "
					+ "history is retired with `active: false`, which keeps it off the entry screen and leaves "
					+ "its statement readable.")
	public void delete(@PathVariable String id) {
		accounts.delete(id);
	}
}

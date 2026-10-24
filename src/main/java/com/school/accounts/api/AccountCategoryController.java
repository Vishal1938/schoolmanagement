package com.school.accounts.api;

import java.util.List;

import com.school.accounts.app.AccountCategoryService;
import com.school.accounts.domain.EntryType;
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

/** What a line in the books is for. */
@RestController
@RequestMapping("/accounts/categories")
@Tag(name = "Accounts", description = "The expense book: money accounts, categories, entries and transfers")
public class AccountCategoryController {

	private final AccountCategoryService categories;

	public AccountCategoryController(AccountCategoryService categories) {
		this.categories = categories;
	}

	@GetMapping
	@PreAuthorize(HasPermission.ACCOUNTS_READ_OR_MANAGE)
	@Operation(summary = "List categories",
			description = "By name within type. Filter with `type=EXPENSE|INCOME` and `active=true`. The three "
					+ "with `system: true` — Fees collected, Salaries, Staff advances — are the application's "
					+ "own: show them in reports, but never offer them on the entry form, because the API "
					+ "refuses them. `asset: true` marks Staff advances, which is in neither the income nor "
					+ "the expense total. Seeds the standard list on the first call this deployment makes.")
	public List<AccountCategoryResponse> list(@RequestParam(required = false) EntryType type,
			@RequestParam(required = false) Boolean active) {
		return categories.list(type, active).stream().map(AccountCategoryResponse::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.ACCOUNTS_MANAGE)
	@Operation(summary = "Add a category",
			description = "A name is unique per type → 409 otherwise, so a school may have both an Interest "
					+ "income and an Interest expense. Nothing added here is ever a system or asset category.")
	public AccountCategoryResponse create(@Valid @RequestBody AccountCategoryRequest request) {
		return AccountCategoryResponse.of(categories.create(request));
	}

	@PutMapping("/{id}")
	@PreAuthorize(HasPermission.ACCOUNTS_MANAGE)
	@Operation(summary = "Rename a category or retire it",
			description = "`type` is ignored: a year of entries is already filed on one side of the books. "
					+ "422 for one of the application's own categories — renaming or retiring those would break "
					+ "the automatic entries booked against them.")
	public AccountCategoryResponse update(@PathVariable String id,
			@Valid @RequestBody AccountCategoryRequest request) {
		return AccountCategoryResponse.of(categories.update(id, request));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(HasPermission.ACCOUNTS_MANAGE)
	@Operation(summary = "Delete a category",
			description = "Only while nothing has ever been filed under it → 409 otherwise, and 422 for a "
					+ "system category. One with history is retired with `active: false`.")
	public void delete(@PathVariable String id) {
		categories.delete(id);
	}
}

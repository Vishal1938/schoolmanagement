package com.school.accounts.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.school.accounts.app.AccountTransferService;
import com.school.accounts.domain.AccountTransfer;
import com.school.common.pagination.PageResponse;
import com.school.common.security.HasPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Money moved between two of the school's own accounts. */
@RestController
@RequestMapping("/accounts/transfers")
@Tag(name = "Accounts", description = "The expense book: money accounts, categories, entries and transfers")
public class AccountTransferController {

	private final AccountTransferService transfers;

	public AccountTransferController(AccountTransferService transfers) {
		this.transfers = transfers;
	}

	@GetMapping
	@PreAuthorize(HasPermission.ACCOUNTS_READ_OR_MANAGE)
	@Operation(summary = "List transfers, newest first",
			description = "Optionally narrowed to a date range, both ends inclusive.")
	public PageResponse<TransferResponse> list(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			Pageable pageable) {
		Page<AccountTransfer> page = transfers.list(from, to, pageable);
		Map<String, String> names = transfers.accountNamesOf(page.getContent());
		return PageResponse.of(page, transfer -> TransferResponse.of(transfer, names));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.ACCOUNTS_MANAGE)
	@Operation(summary = "Record a transfer between two accounts",
			description = "`{date, fromAccountId, toAccountId, amount, reference, note}` — the week's cash "
					+ "banked, say. **Neither income nor expense**: it appears in no month's totals and only "
					+ "moves two balances. Append-only, like a payment: a transfer entered the wrong way round "
					+ "is corrected by one going back. 422 for the same account twice, a future date, a "
					+ "retired account, or a date inside a closed period. It does **not** check that the source "
					+ "account has the money — a cash book can legitimately go briefly negative on paper, and "
					+ "the balance is visible.")
	public TransferResponse create(@Valid @RequestBody TransferRequest request) {
		AccountTransfer transfer = transfers.create(request);
		return TransferResponse.of(transfer, transfers.accountNamesOf(List.of(transfer)));
	}
}

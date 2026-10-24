package com.school.accounts.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.school.accounts.app.AccountEntryService;
import com.school.accounts.app.EntryAttachmentService;
import com.school.accounts.app.EntrySearch;
import com.school.accounts.domain.AccountCategory;
import com.school.accounts.domain.AccountEntry;
import com.school.accounts.domain.EntrySource;
import com.school.accounts.domain.EntryStatus;
import com.school.accounts.domain.EntryType;
import com.school.common.pagination.PageResponse;
import com.school.common.security.HasPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * The day book.
 *
 * <p>{@code @PreAuthorize} only decides who may call. Whether a particular line may be changed is
 * an object-level rule in {@link AccountEntryService}: only what somebody typed in is editable, and
 * nothing dated inside a closed period is.
 */
@RestController
@RequestMapping("/accounts/entries")
@Tag(name = "Accounts", description = "The expense book: money accounts, categories, entries and transfers")
public class AccountEntryController {

	private final AccountEntryService entries;
	private final EntryAttachmentService attachments;

	public AccountEntryController(AccountEntryService entries, EntryAttachmentService attachments) {
		this.entries = entries;
		this.attachments = attachments;
	}

	@GetMapping
	@PreAuthorize(HasPermission.ACCOUNTS_READ_OR_MANAGE)
	@Operation(summary = "The day book, filtered and paged",
			description = "Newest first unless a sort is given. Every filter is optional and they combine "
					+ "with AND: `from`/`to` (inclusive, on the entry's own date), `type`, `category` (id), "
					+ "`account` (money account id), `status`, `source`, and `q` as a prefix against the party "
					+ "and the bill number. `editable: false` marks the lines the application wrote itself — "
					+ "change those in fees or payroll, not here.")
	public PageResponse<AccountEntryResponse> list(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) EntryType type,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) String account,
			@RequestParam(required = false) EntryStatus status,
			@RequestParam(required = false) EntrySource source,
			@RequestParam(required = false) String q,
			Pageable pageable) {
		Page<AccountEntry> page = entries.search(
				new EntrySearch(from, to, type, category, account, status, source, q), pageable);
		return PageResponse.of(page, toResponse(page.getContent()));
	}

	@GetMapping("/{id}")
	@PreAuthorize(HasPermission.ACCOUNTS_READ_OR_MANAGE)
	@Operation(summary = "One entry")
	public AccountEntryResponse get(@PathVariable String id) {
		AccountEntry entry = entries.get(id);
		return toResponse(List.of(entry)).apply(entry);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.ACCOUNTS_MANAGE)
	@Operation(summary = "Record an expense or an income",
			description = "`amount` is paise and positive — the direction is `type`. `status` defaults to "
					+ "PAID, which needs `moneyAccountId` and `mode`; UNPAID is a bill payable, needs "
					+ "`dueDate`, and is only valid for an EXPENSE (money owed **to** the school is a fee "
					+ "invoice). 422 for one of the application's own categories, for a retired category or "
					+ "account, for a category on the wrong side of the books, for a future date, and for "
					+ "anything dated inside a closed period.")
	public AccountEntryResponse create(@Valid @RequestBody AccountEntryRequest request) {
		AccountEntry entry = entries.create(request);
		return toResponse(List.of(entry)).apply(entry);
	}

	@PutMapping("/{id}")
	@PreAuthorize(HasPermission.ACCOUNTS_MANAGE)
	@Operation(summary = "Replace a manual entry",
			description = "Send the whole line. May move between PAID and UNPAID — a bill entered as paid by "
					+ "mistake is the commonest correction there is. 422 for an entry written from a fee "
					+ "payment, a salary or an advance: change those where they were recorded and the books "
					+ "follow. Both the old and the new date are checked against the month lock.")
	public AccountEntryResponse update(@PathVariable String id, @Valid @RequestBody AccountEntryRequest request) {
		AccountEntry entry = entries.update(id, request);
		return toResponse(List.of(entry)).apply(entry);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(HasPermission.ACCOUNTS_MANAGE)
	@Operation(summary = "Delete a manual entry",
			description = "422 for an automatic one, and for anything inside a closed period. The deleted "
					+ "line is kept in the audit trail, so a mistake is recoverable.")
	public void delete(@PathVariable String id) {
		entries.delete(id);
	}

	@PostMapping("/{id}/pay")
	@PreAuthorize(HasPermission.ACCOUNTS_MANAGE)
	@Operation(summary = "Settle a bill payable",
			description = "`{paidOn, moneyAccountId, mode, reference}`. `paidOn` defaults to today and may "
					+ "not be in the future. There is no amount: a bill is paid in full or it is two bills. "
					+ "409 if it has already been paid.")
	public AccountEntryResponse pay(@PathVariable String id, @Valid @RequestBody PayEntryRequest request) {
		AccountEntry entry = entries.pay(id, request);
		return toResponse(List.of(entry)).apply(entry);
	}

	// --- attachments ------------------------------------------------------------------------------

	@PostMapping(path = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize(HasPermission.ACCOUNTS_MANAGE)
	@Operation(summary = "Upload a bill",
			description = "PDF, JPEG or PNG up to 10 MB, identified by the file's own bytes rather than its "
					+ "Content-Type. Stored privately. Put the returned object into the entry's `attachments` "
					+ "array — uploading alone saves nothing. At most 5 per entry.")
	public EntryAttachmentResponse upload(@RequestParam("file") MultipartFile file) {
		return EntryAttachmentResponse.of(attachments.upload(file));
	}

	/**
	 * Streams a bill. Inline, so a PDF or a photograph opens in the browser rather than landing in
	 * the downloads folder, with the uploader's file name for whoever does save it.
	 */
	@GetMapping("/attachments/{key}")
	@PreAuthorize(HasPermission.ACCOUNTS_READ_OR_MANAGE)
	@Operation(summary = "Download a bill",
			description = "Streams the file; the bytes never leave this application's authentication. A "
					+ "vendor invoice names amounts and bank details, so this is not a public URL.")
	public ResponseEntity<byte[]> download(@PathVariable String key) {
		EntryAttachmentService.Download file = attachments.download(key);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.inline().filename(file.fileName()).build().toString())
				.cacheControl(CacheControl.noStore())
				.body(file.bytes());
	}

	// --- internals --------------------------------------------------------------------------------

	/**
	 * The mapper for a page of entries, with the category and account names resolved in two reads
	 * rather than two per row.
	 */
	private Function<AccountEntry, AccountEntryResponse> toResponse(List<AccountEntry> page) {
		Map<String, AccountCategory> categories = entries.categoriesOf(page);
		Map<String, String> accountNames = entries.accountNamesOf(page);
		return entry -> AccountEntryResponse.of(entry, categories, accountNames);
	}
}

package com.school.payroll.api;

import com.school.common.security.HasPermission;
import com.school.payroll.app.PayrollService;
import com.school.payroll.app.SalarySlip;
import com.school.payroll.app.SalarySlipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payroll runs, the records they produce, paying them out, and salary slips.
 *
 * <p>{@code @PreAuthorize} only decides who may call at all. <em>Whose</em> record a caller may read
 * is an object-level rule in {@link PayrollService#requireReadable}: an admin, or the employee the
 * record belongs to. That is what lets one slip endpoint serve the office and the staff room.
 */
@RestController
@RequestMapping("/payroll")
@Tag(name = "Payroll", description = "Salary structures, advances, runs and slips")
public class PayrollController {

	private final PayrollService payroll;
	private final SalarySlipService slips;

	public PayrollController(PayrollService payroll, SalarySlipService slips) {
		this.payroll = payroll;
		this.slips = slips;
	}

	// --- runs -------------------------------------------------------------------------------------

	@PostMapping("/runs")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.PAYROLL_MANAGE)
	@Operation(summary = "Compute one month's salaries",
			description = "ADMIN. Writes one PENDING record per ACTIVE employee who has a salary "
					+ "structure effective for the month, deducting any advance installment. **Idempotent**: "
					+ "employees who already have a record for the month come back under `skipped` and are "
					+ "left untouched, so fixing a structure and running again is the normal way to finish a "
					+ "run. `missingStructure` names the active employees nothing could be computed for. With "
					+ "`prorateByAttendance`, pay is docked for ABSENT and HALF_DAY in the employee register; "
					+ "LEAVE, holidays, non-working days and unmarked days are paid. 422 for a future month.")
	public PayrollRunResponse run(@Valid @RequestBody PayrollRunRequest request) {
		return payroll.run(request);
	}

	@GetMapping("/runs/{month}")
	@PreAuthorize(HasPermission.PAYROLL_MANAGE)
	@Operation(summary = "One month's payroll register and totals",
			description = "ADMIN. `month` is `yyyy-MM`. Records are by employee name, and the totals are "
					+ "in paise: gross, net, and net split into what has been paid and what is still pending. "
					+ "A month nobody has run comes back empty rather than 404.")
	public PayrollMonthResponse month(@PathVariable String month) {
		return payroll.month(month);
	}

	// --- paying out -------------------------------------------------------------------------------

	@PostMapping("/records/{id}/pay")
	@PreAuthorize(HasPermission.PAYROLL_MANAGE)
	@Operation(summary = "Mark one salary paid",
			description = "ADMIN. Takes this month's advance installment off the advance balance, "
					+ "closing the advance when the last of it comes back. `paidOn` defaults to today and may "
					+ "not be in the future. 409 if it has already been paid — paying twice would recover the "
					+ "advance twice.")
	public PayrollRecordResponse pay(@PathVariable String id, @Valid @RequestBody PayRecordRequest request) {
		return payroll.pay(id, request);
	}

	@PostMapping("/records/pay-bulk")
	@PreAuthorize(HasPermission.PAYROLL_MANAGE)
	@Operation(summary = "Mark many salaries paid in one bank run",
			description = "ADMIN. Same effect as paying each one, without a per-record reference. "
					+ "Forgiving by design: ids already paid, or gone because the month was re-run, are "
					+ "reported back rather than failing the batch, and replaying the same list pays nothing "
					+ "twice.")
	public PayBulkResponse payBulk(@Valid @RequestBody PayBulkRequest request) {
		return payroll.payBulk(request);
	}

	@DeleteMapping("/records/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(HasPermission.PAYROLL_MANAGE)
	@Operation(summary = "Discard a pending salary record",
			description = "ADMIN. For re-running a month after fixing a structure. 409 for a record "
					+ "already paid: a paid salary is a fact, and correcting one is a decision for the office "
					+ "rather than a DELETE. The discarded record is kept in the audit trail.")
	public void delete(@PathVariable String id) {
		payroll.delete(id);
	}

	// --- the employee's own view -------------------------------------------------------------------

	@GetMapping("/mine")
	@PreAuthorize(HasPermission.PAYROLL_READ_SELF)
	@Operation(summary = "Your own salary records, newest month first",
			description = "TEACHER and STAFF with a login, and ADMIN for their own. Totals are in paise: "
					+ "what you have been paid, and what is computed but still pending. Empty rather than 404 "
					+ "for somebody who joined this month.")
	public MyPayrollResponse mine() {
		return payroll.mine();
	}

	/**
	 * Served inline so a browser shows the slip rather than dropping a file in the downloads folder;
	 * the filename is still there for whoever does save it.
	 */
	@GetMapping(value = "/records/{id}/slip.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	@PreAuthorize(HasPermission.AUTHENTICATED)
	@Operation(summary = "The salary slip for one record, as a PDF",
			description = "ADMIN, or the employee the record belongs to; anybody else gets 403. School "
					+ "header, earnings and deductions in full, advance recovery, loss of pay, and the net in "
					+ "figures and words. A PENDING record has a slip too, marked PENDING on its face.")
	public ResponseEntity<byte[]> slip(@PathVariable String id) {
		SalarySlip slip = slips.forRecord(id);
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.inline().filename(slip.fileName()).build().toString())
				.cacheControl(CacheControl.noStore())
				.body(slip.pdf());
	}
}

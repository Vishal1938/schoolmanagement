package com.school.fees.api;

import com.school.common.security.HasPermission;
import com.school.fees.app.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One student's fees.
 *
 * <p>Two endpoints rather than one projection, because the audiences are different in kind: the
 * ledger carries amounts and is for an admin or the family, while the status carries none and is for
 * a class teacher. {@code @PreAuthorize} only decides who may call at all — <em>whose</em> records a
 * caller may read is an object-level rule in {@link InvoiceService}.
 */
@RestController
@RequestMapping("/fees/students")
@Tag(name = "Fees", description = "Fee heads, structures, invoices and concessions")
public class StudentFeesController {

	private final InvoiceService invoices;

	public StudentFeesController(InvoiceService invoices) {
		this.invoices = invoices;
	}

	@GetMapping("/{uniqueId}")
	@PreAuthorize(HasPermission.AUTHENTICATED)
	@Operation(summary = "A student's invoices and totals",
			description = "ADMIN, or that student (which is also how a parent sees it). A teacher gets 403 — "
					+ "they get /status, which has no amounts. lateFineDue is computed now and never stored; "
					+ "balance is netAmount - paidAmount + lateFineDue. All amounts are paise.")
	public StudentFeesResponse ledger(@PathVariable String uniqueId) {
		return invoices.ledgerFor(uniqueId);
	}

	@GetMapping("/{uniqueId}/status")
	@PreAuthorize(HasPermission.FEE_READ_STATUS)
	@Operation(summary = "A student's fee status, with no amounts",
			description = "ADMIN and teachers for anybody, a student only for themselves. PAID when nothing is "
					+ "outstanding (including a student with no invoices), OVERDUE when something is still owed "
					+ "past its due date plus the grace days, PARTIAL when something has been paid and a balance "
					+ "remains, DUE otherwise.")
	public StudentFeeStatusResponse status(@PathVariable String uniqueId) {
		return invoices.statusForCaller(uniqueId);
	}
}

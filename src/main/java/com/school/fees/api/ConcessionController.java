package com.school.fees.api;

import java.util.List;

import com.school.common.security.HasPermission;
import com.school.fees.app.ConcessionService;
import com.school.fees.app.InvoiceService;
import com.school.fees.domain.Concession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Concessions: reductions granted to one student for one session. */
@RestController
@RequestMapping("/fees/concessions")
@Tag(name = "Fees", description = "Fee heads, structures, invoices and concessions")
public class ConcessionController {

	private final ConcessionService concessions;
	private final InvoiceService invoices;

	public ConcessionController(ConcessionService concessions, InvoiceService invoices) {
		this.concessions = concessions;
		this.invoices = invoices;
	}

	@GetMapping
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "List concessions",
			description = "With studentUniqueId, every concession that student has ever had; without it, every "
					+ "concession granted in the active session. Newest first.")
	public List<ConcessionResponse> list(@RequestParam(required = false) String studentUniqueId) {
		return concessions.list(studentUniqueId).stream()
				.map(concession -> ConcessionResponse.of(concession, concessions.studentNameOf(concession)))
				.toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Grant a concession",
			description = "Always for the active session. value is a whole percentage 1–100 for PERCENT, or "
					+ "paise for FIXED. An empty headIds means every head. This applies to invoices generated "
					+ "afterwards; use /apply for invoices that already exist.")
	public ConcessionResponse create(@Valid @RequestBody ConcessionRequest request) {
		Concession saved = concessions.create(request);
		return ConcessionResponse.of(saved, concessions.studentNameOf(saved));
	}

	@PostMapping("/{id}/apply")
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Recalculate this student's UNPAID invoices",
			description = "Rewrites the concession and netAmount on the student's UNPAID invoices for that "
					+ "session, from all of their concessions rather than only this one. PARTIAL, PAID and "
					+ "CANCELLED invoices are left alone — restating a bill already paid into is a credit note, "
					+ "not an edit. Each rewritten invoice is audited with its before and after.")
	public ApplyConcessionResponse apply(@PathVariable String id) {
		return invoices.applyConcession(id);
	}
}

package com.school.fees.api;

import java.util.List;

import com.school.common.security.HasPermission;
import com.school.fees.app.FeeReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fee reports built from invoices.
 *
 * <p>{@code /fees/reports/collection} is not here — it reports payments, so it lives in the payment
 * module, which already depends on this one.
 */
@RestController
@RequestMapping("/fees/reports")
@Tag(name = "Fees", description = "Fee heads, structures, invoices and concessions")
public class FeeReportController {

	private final FeeReportService reports;

	public FeeReportController(FeeReportService reports) {
		this.reports = reports;
	}

	@GetMapping("/defaulters")
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Students who still owe something",
			description = "Defaults to the active session; pass classId for one class. balance includes the "
					+ "late fine running right now. Ordered by the oldest unpaid due date, because the office "
					+ "chases whoever has been behind longest, not whoever owes most.")
	public List<DefaulterResponse> defaulters(
			@RequestParam(required = false) String sessionId,
			@RequestParam(required = false) String classId) {
		return reports.defaulters(sessionId, classId);
	}
}

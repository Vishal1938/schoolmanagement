package com.school.fees.api;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ValidationException;
import com.school.common.security.HasPermission;
import com.school.fees.app.FeeReportService;
import com.school.fees.domain.InstallmentKind;
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
					+ "chases whoever has been behind longest, not whoever owes most. Narrow with kind "
					+ "(MONTHLY, SEMESTER, ONE_TIME or CUSTOM) and with installment — an installment's name or "
					+ "label, `Semester 3` or `Oct 2026` — which together answer \"who has not paid Semester "
					+ "3\". `month` as `2026-10` is the monthly-only predecessor of installment and is still "
					+ "accepted. Every figure in a row counts only the invoices that got through the filters, "
					+ "so a filtered balance is smaller than the student's real one.")
	public List<DefaulterResponse> defaulters(
			@RequestParam(required = false) String sessionId,
			@RequestParam(required = false) String classId,
			@RequestParam(required = false) InstallmentKind kind,
			@RequestParam(required = false) String installment,
			@RequestParam(required = false) String month) {
		return reports.defaulters(sessionId, classId, kind, installment, parsedMonth(month));
	}

	/**
	 * {@code month} as a {@link YearMonth}, or null when it was not sent.
	 *
	 * <p>Parsed here rather than declared as a {@code YearMonth} parameter: Spring's default conversion
	 * service has no {@code String → YearMonth} converter — {@code YearMonth} has no
	 * {@code valueOf(String)} or {@code of(String)} for it to find — so the binding would fail with an
	 * unhelpful 500 rather than telling the caller what shape the value should be.
	 */
	private static YearMonth parsedMonth(String month) {
		if (month == null || month.isBlank()) {
			return null;
		}
		try {
			return YearMonth.parse(month.trim());
		}
		catch (DateTimeParseException ex) {
			throw new ValidationException("That is not a month",
					List.of(new FieldViolation("month", "must be a month like 2026-10")));
		}
	}
}

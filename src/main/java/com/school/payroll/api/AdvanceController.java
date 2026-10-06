package com.school.payroll.api;

import java.util.List;

import com.school.common.security.HasPermission;
import com.school.payroll.app.SalaryAdvanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Advances against salary. ADMIN only; an employee sees the recovery on their slip. */
@RestController
@RequestMapping("/payroll/advances")
@Tag(name = "Payroll", description = "Salary structures, advances, runs and slips")
public class AdvanceController {

	private final SalaryAdvanceService advances;

	public AdvanceController(SalaryAdvanceService advances) {
		this.advances = advances;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.PAYROLL_MANAGE)
	@Operation(summary = "Record an advance",
			description = "ADMIN. Recovered automatically from the next payroll runs, "
					+ "`monthlyRecovery` at a time, with the last installment being whatever is left. The "
					+ "balance moves when a salary is actually **paid**, not when a run plans it, so deleting "
					+ "a pending record and re-running costs nobody an installment.")
	public AdvanceResponse give(@Valid @RequestBody AdvanceRequest request) {
		return AdvanceResponse.of(advances.give(request));
	}

	@GetMapping
	@PreAuthorize(HasPermission.PAYROLL_MANAGE)
	@Operation(summary = "Advances, by employee or everything still owed",
			description = "ADMIN. With `employeeUniqueId`, that employee's advances newest first, "
					+ "including closed ones. Without it, every advance still being recovered across the "
					+ "school — the list the office keeps open.")
	public List<AdvanceResponse> list(@RequestParam(required = false) String employeeUniqueId) {
		return advances.list(employeeUniqueId).stream().map(AdvanceResponse::of).toList();
	}
}

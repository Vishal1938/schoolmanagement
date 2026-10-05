package com.school.payroll.api;

import com.school.common.security.HasPermission;
import com.school.payroll.app.SalaryStructureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Salary structures. ADMIN only, both ways: what somebody is paid is not something their colleagues
 * — or they themselves — read here. An employee sees their own figures on their salary slip.
 */
@RestController
@RequestMapping("/payroll/structures")
@Tag(name = "Payroll", description = "Salary structures, advances, runs and slips")
public class SalaryStructureController {

	private final SalaryStructureService structures;

	public SalaryStructureController(SalaryStructureService structures) {
		this.structures = structures;
	}

	@GetMapping("/{employeeUniqueId}")
	@PreAuthorize(HasPermission.PAYROLL_MANAGE)
	@Operation(summary = "One employee's salary structure, current and historical",
			description = "ADMIN. `current` is the version in force today, or null when every version "
					+ "there is starts in the future. `history` is every version, newest first — nothing is "
					+ "ever overwritten, so a slip printed last year stays explainable. An employee with no "
					+ "structure yet is not a 404: current is null and history is empty.")
	public SalaryStructureResponse view(@PathVariable String employeeUniqueId) {
		return structures.view(employeeUniqueId);
	}

	@PutMapping("/{employeeUniqueId}")
	@PreAuthorize(HasPermission.PAYROLL_MANAGE)
	@Operation(summary = "Save a new version of the structure",
			description = "ADMIN. A PUT that **creates a version** rather than replacing one: send the "
					+ "whole structure, and whatever you leave out is gone from the new version while earlier "
					+ "versions stay exactly as they were. Percentage deductions are in basis points — 1200 "
					+ "is 12%. 422 if the deductions come to more than the gross, which would otherwise pay "
					+ "somebody zero every month until a human noticed.")
	public SalaryStructureResponse save(@PathVariable String employeeUniqueId,
			@Valid @RequestBody SalaryStructureRequest request) {
		return structures.save(employeeUniqueId, request);
	}
}

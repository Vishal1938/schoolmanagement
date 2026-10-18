package com.school.fees.api;

import com.school.common.security.HasPermission;
import com.school.fees.app.FeeRegisterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The fee register: a class as a grid, students down and installments across.
 *
 * <p>{@code POST /fees/register/mark-paid} is not here — taking money is the payment module's, and it
 * is served alongside the other counter endpoints in {@code FeeCounterController}. Both appear under
 * {@code /fees/register} to a client, which is what matters.
 */
@RestController
@RequestMapping("/fees/register")
@Tag(name = "Fees", description = "Fee heads, structures, invoices and concessions")
public class FeeRegisterController {

	private final FeeRegisterService register;

	public FeeRegisterController(FeeRegisterService register) {
		this.register = register;
	}

	@GetMapping
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "The fee collection register for one class",
			description = "Columns are the class's MONTHLY installments in due-date order, then its ONE_TIME "
					+ "fees in due-date order. Rows are the ACTIVE students in roll order, with `cells` keyed by "
					+ "column key — a column a student has no invoice for has no entry at all, which is not the "
					+ "same as unpaid. `otherCharges` carries that student's individual CUSTOM charges, which "
					+ "have no column. The footer gives collected and pending per column and overall. "
					+ "Defaults to the active session; pass section to narrow to one. All amounts are paise, and "
					+ "late fines are deliberately excluded so that due - paid = balance holds everywhere.")
	public FeeRegisterResponse get(
			@RequestParam String classId,
			@RequestParam(required = false) String section,
			@RequestParam(required = false) String sessionId) {
		return register.register(sessionId, classId, section);
	}
}

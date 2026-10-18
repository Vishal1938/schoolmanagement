package com.school.fees.api;

import com.school.common.security.HasPermission;
import com.school.fees.app.StudentChargeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Charges raised against named students: a fine, a trip, a replacement book. */
@RestController
@RequestMapping("/fees/charges")
@Tag(name = "Fees", description = "Fee heads, structures, invoices and concessions")
public class StudentChargeController {

	private final StudentChargeService charges;

	public StudentChargeController(StudentChargeService charges) {
		this.charges = charges;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Charge named students something one-off",
			description = "Raises one CUSTOM invoice per student at the same amount, under the fee head given. "
					+ "All or nothing: an unknown student id or a retired head rejects the whole request with "
					+ "every problem listed. The charges appear on each student's ledger and in the register's "
					+ "otherCharges, are paid through the ordinary counter and gateway flows, and carry no late "
					+ "fine. Amounts are paise.")
	public StudentChargeResponse raise(@Valid @RequestBody StudentChargeRequest request) {
		return charges.raise(request);
	}

	@DeleteMapping("/{invoiceId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Withdraw an individual charge",
			description = "Only while nothing has been paid against it → 422 otherwise, because a receipt "
					+ "naming it has been issued. Only CUSTOM invoices can be deleted; one raised from a fee "
					+ "structure is not an individual charge. This is the one place in fees where an invoice is "
					+ "deleted rather than cancelled: nothing points at a charge nobody has paid towards.")
	public void delete(@PathVariable String invoiceId) {
		charges.delete(invoiceId);
	}
}

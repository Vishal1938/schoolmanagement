package com.school.fees.api;

import java.util.List;
import java.util.Map;

import com.school.common.security.HasPermission;
import com.school.fees.app.FeeStructureService;
import com.school.fees.app.InvoiceService;
import com.school.fees.domain.FeeStructure;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Fee structures, and raising the invoices from one. */
@RestController
@RequestMapping("/fees/structures")
@Tag(name = "Fees", description = "Fee heads, structures, invoices and concessions")
public class FeeStructureController {

	private final FeeStructureService structures;
	private final InvoiceService invoices;

	public FeeStructureController(FeeStructureService structures, InvoiceService invoices) {
		this.structures = structures;
		this.invoices = invoices;
	}

	@GetMapping
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "List fee structures",
			description = "Defaults to the active session. With classId, returns the one structure that class "
					+ "has for that session, or an empty list.")
	public List<FeeStructureResponse> list(
			@RequestParam(required = false) String sessionId,
			@RequestParam(required = false) String classId) {
		List<FeeStructure> found = structures.list(sessionId, classId);
		return toResponses(found);
	}

	@GetMapping("/{id}")
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "One fee structure")
	public FeeStructureResponse get(@PathVariable String id) {
		return toResponses(List.of(structures.get(id))).getFirst();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Create a class's fee structure for the active session",
			description = "sessionId is not in the body — it is always the active session. One structure per "
					+ "(session, class) → 409 if that class already has one. Every headId must exist and still "
					+ "be active, installment names must be unique, and amounts are paise.")
	public FeeStructureResponse create(@Valid @RequestBody FeeStructureRequest request) {
		return toResponses(List.of(structures.create(request))).getFirst();
	}

	@PutMapping("/{id}")
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Replace a structure's installments and late-fine rule",
			description = "The classId must match the structure being edited. An installment that already has "
					+ "invoices cannot be renamed or dropped → 422, because its name is part of the idempotency "
					+ "key. Amounts already invoiced are not rewritten; the late-fine rule is read live, so "
					+ "changing it changes every invoice at once.")
	public FeeStructureResponse update(@PathVariable String id, @Valid @RequestBody FeeStructureRequest request) {
		return toResponses(List.of(structures.update(id, request))).getFirst();
	}

	@PostMapping("/{id}/generate-invoices")
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Raise invoices for every ACTIVE student of this class",
			description = "One invoice per student per installment, with that student's concessions applied. "
					+ "Idempotent: a row that already exists is counted in `skipped`, so running it again after "
					+ "an admission writes only the invoices that student is missing.")
	public GenerateInvoicesResponse generateInvoices(@PathVariable String id) {
		return invoices.generate(id);
	}

	/** Resolves class and head names for the whole batch in two reads rather than per structure. */
	private List<FeeStructureResponse> toResponses(List<FeeStructure> found) {
		Map<String, String> headNames = structures.headNamesOf(found);
		return found.stream()
				.map(structure -> FeeStructureResponse.of(structure, structures.classNameOf(structure), headNames))
				.toList();
	}
}

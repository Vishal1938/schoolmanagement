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
import org.springframework.web.bind.annotation.DeleteMapping;
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

	@PostMapping("/monthly")
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Set up a year of monthly fees for one class",
			description = "Writes one MONTHLY installment per month, named \"Jul 2026\", into the structure for "
					+ "that session and class, creating the structure if there is none. Run it again to correct "
					+ "the figure: months with no invoices yet are rewritten, months that already have invoices "
					+ "are left alone and reported in monthsSkipped. Other heads already on those months are not "
					+ "touched. sectionAmounts overrides the amount per section, which is how Year 1 and Year 2 "
					+ "of one course charge differently. Amounts are paise. Nothing is billed here — run "
					+ "generate-invoices afterwards.")
	public MonthlyFeeSetupResponse setUpMonthly(@Valid @RequestBody MonthlyFeeSetupRequest request) {
		FeeStructureService.MonthlyPlanResult result = structures.setUpMonthly(request);
		return new MonthlyFeeSetupResponse(result.created(), result.updated(), result.skipped(),
				toResponses(List.of(result.structure())).getFirst());
	}

	@PostMapping("/semester")
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Set up a session's semester fees for one course",
			description = "Writes one SEMESTER installment per entry, named \"Semester 3\" and labelled "
					+ "\"Semester 3 (Jul-Dec 2026)\", into the structure for that session and course, creating "
					+ "the structure if there is none and extending it if there is. Each semester carries its "
					+ "own periodStart and periodEnd, which is the one thing a month does not need: a semester's "
					+ "period cannot be worked out from its due date. Run it again to correct the figure — "
					+ "semesters with no invoices yet are rewritten, ones that already have invoices are left "
					+ "alone and reported in semestersSkipped. A duplicate semester number, or periods that "
					+ "overlap each other or a semester already on the structure, is 400. sectionAmounts "
					+ "overrides the amount per section. Amounts are paise. Nothing is billed here — run "
					+ "generate-invoices afterwards.")
	public SemesterFeeSetupResponse setUpSemesters(@Valid @RequestBody SemesterFeeSetupRequest request) {
		FeeStructureService.SemesterPlanResult result = structures.setUpSemesters(request);
		return new SemesterFeeSetupResponse(result.created(), result.updated(), result.skipped(),
				toResponses(List.of(result.structure())).getFirst());
	}

	@GetMapping("/semester/suggest")
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Two prefilled semesters for a course",
			description = "A suggestion to review, not a commitment: it reads only the course name and the "
					+ "session's dates and writes nothing. The numbers come from a \"Year N\" in the course "
					+ "name — Year 2 suggests semesters 3 and 4 — falling back to 1 and 2. The periods are "
					+ "Jul 1 to Dec 31 and Jan 1 to Jun 30 of the session, falling due on Jul 31 and Jan 31. "
					+ "Edit anything and POST it to /fees/structures/semester.")
	public SemesterSuggestionResponse suggestSemesters(@RequestParam String classId,
			@RequestParam(required = false) String sessionId) {
		return structures.suggestSemesters(classId, sessionId);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Delete a fee structure",
			description = "Removes the structure and the invoices raised from it. Refused (422) if a single "
					+ "rupee has been taken against any of them — a receipt has then been issued naming one of "
					+ "its installments. Audited as FEE_STRUCTURE_DELETED with the counts.")
	public void delete(@PathVariable String id) {
		structures.delete(id);
	}

	@DeleteMapping("/{id}/installments/{name}")
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Remove one installment from a structure",
			description = "Works on any kind — a month, a semester or a one-off fee — and removes the "
					+ "invoices raised from it. Refused (422) if anything has been paid against any of them. "
					+ "Returns the structure as it now stands. Audited as FEE_INSTALLMENT_DELETED.")
	public FeeStructureResponse deleteInstallment(@PathVariable String id, @PathVariable String name) {
		return toResponses(List.of(structures.deleteInstallment(id, name))).getFirst();
	}

	@PostMapping("/{id}/one-time-fees")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Add a course-wide one-off fee",
			description = "Adds a ONE_TIME installment — \"Admission fee\", \"Laboratory deposit\" — to the "
					+ "structure. 409 if an installment of that name already exists. Nothing is billed here: "
					+ "running generate-invoices again raises this fee for every student on the roll and nothing "
					+ "else, because the months already have their invoices. Amounts are paise.")
	public FeeStructureResponse addOneTimeFee(@PathVariable String id, @Valid @RequestBody OneTimeFeeRequest request) {
		return toResponses(List.of(structures.addOneTimeFee(id, request))).getFirst();
	}

	@PutMapping("/{id}/one-time-fees/{name}")
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Edit a course-wide one-off fee",
			description = "The name in the path identifies it. Head, amount, section overrides and due date are "
					+ "all editable; invoices already raised keep the amounts they were issued with, as they do "
					+ "for any structure edit. Renaming is refused once it has invoices (422), because the name "
					+ "is part of the idempotency key. 422 as well if the named installment is a month rather "
					+ "than a one-off fee.")
	public FeeStructureResponse updateOneTimeFee(@PathVariable String id, @PathVariable String name,
			@Valid @RequestBody OneTimeFeeRequest request) {
		return toResponses(List.of(structures.updateOneTimeFee(id, name, request))).getFirst();
	}

	@DeleteMapping("/{id}/one-time-fees/{name}")
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Remove a course-wide one-off fee",
			description = "Removes the installment and the invoices raised from it. Refused (422) if a single "
					+ "rupee has been taken against any of them — a receipt has then been issued naming it. "
					+ "Returns the structure as it now stands.")
	public FeeStructureResponse deleteOneTimeFee(@PathVariable String id, @PathVariable String name) {
		return toResponses(List.of(structures.deleteOneTimeFee(id, name))).getFirst();
	}

	@PostMapping("/{id}/generate-invoices")
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Raise invoices for every ACTIVE student of this class",
			description = "One invoice per student per installment, with that student's concessions applied and "
					+ "each section's own amount where an item has one. Idempotent: a row that already exists is "
					+ "counted in `skipped`, so running it again after an admission — or after adding a one-off "
					+ "fee — writes only the invoices that are missing.")
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

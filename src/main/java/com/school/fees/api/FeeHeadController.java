package com.school.fees.api;

import java.util.List;

import com.school.common.security.HasPermission;
import com.school.fees.app.FeeHeadService;
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

/** Fee heads: the things the school charges for. */
@RestController
@RequestMapping("/fees/heads")
@Tag(name = "Fees", description = "Fee heads, structures, invoices and concessions")
public class FeeHeadController {

	private final FeeHeadService heads;

	public FeeHeadController(FeeHeadService heads) {
		this.heads = heads;
	}

	@GetMapping
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "List fee heads",
			description = "By name. Pass active=true for the ones currently charged; omit it for every head, "
					+ "including retired ones that old invoices still name.")
	public List<FeeHeadResponse> list(@RequestParam(required = false) Boolean active) {
		return heads.list(active).stream().map(FeeHeadResponse::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Create a fee head",
			description = "Names are unique → 409 otherwise. Defaults to active.")
	public FeeHeadResponse create(@Valid @RequestBody FeeHeadRequest request) {
		return FeeHeadResponse.of(heads.create(request));
	}

	@PutMapping("/{id}")
	@PreAuthorize(HasPermission.FEE_MANAGE)
	@Operation(summary = "Rename a fee head or retire it",
			description = "There is no delete: invoices name their heads, so a head no longer charged is set "
					+ "active=false, which keeps it off new structures and leaves old invoices readable.")
	public FeeHeadResponse update(@PathVariable String id, @Valid @RequestBody FeeHeadRequest request) {
		return FeeHeadResponse.of(heads.update(id, request));
	}
}

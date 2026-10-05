package com.school.common.audit;

import java.time.Instant;

import com.school.common.pagination.PageResponse;
import com.school.common.security.HasPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reads the audit trail. Lives in {@code common} because auditing is cross-cutting rather than owned by
 * any feature module.
 *
 * <p>Authorised on {@link com.school.common.security.Permission#AUDIT_READ}: the trail names who did
 * what, so it is for administrators only.
 */
@RestController
@RequestMapping("/audit")
@Tag(name = "Audit", description = "Who changed what, and when")
public class AuditController {

	private final AuditService service;

	public AuditController(AuditService service) {
		this.service = service;
	}

	@GetMapping
	@PreAuthorize(HasPermission.AUDIT_READ)
	@Operation(summary = "Search the audit trail",
			description = "Newest first. All filters are optional; combine entityType and entityId to follow "
					+ "the history of one record. Entries are never deleted.")
	public PageResponse<AuditLogResponse> search(
			@RequestParam(required = false) String entityType,
			@RequestParam(required = false) String entityId,
			@RequestParam(required = false) AuditAction action,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@PageableDefault(size = 20, sort = "at", direction = Sort.Direction.DESC) Pageable pageable) {
		AuditSearch search = new AuditSearch(entityType, entityId, action, from, to);
		return PageResponse.of(service.search(search, pageable), AuditLogResponse::from);
	}
}

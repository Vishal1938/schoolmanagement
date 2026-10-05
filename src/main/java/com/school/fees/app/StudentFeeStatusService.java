package com.school.fees.app;

import java.util.Collection;
import java.util.Map;

import com.school.common.fees.FeeStatus;
import com.school.common.fees.StudentFeeStatusProvider;
import org.springframework.stereotype.Service;

/**
 * The real implementation of the cross-module fee-status contract, replacing B5's stub that answered
 * {@link FeeStatus#DUE} for everybody.
 *
 * <p>A thin adapter rather than having a service implement the interface directly. It keeps the whole
 * of what leaves this module visible in one short file — a status, never an invoice and never an
 * amount — which is what lets the teacher projection in people carry a fee status without being able
 * to carry a figure (CLAUDE.md rule 2).
 *
 * <p>It delegates to {@link FeeStatusReader} rather than to {@code InvoiceService} on purpose. People
 * depends on this class, so whatever it depends on is pulled into people's startup graph;
 * {@code InvoiceService} reaches {@code ConcessionService}, which needs {@code StudentService}, and
 * that is a constructor cycle. See {@link FeeStatusReader}.
 */
@Service
public class StudentFeeStatusService implements StudentFeeStatusProvider {

	private final FeeStatusReader statuses;

	public StudentFeeStatusService(FeeStatusReader statuses) {
		this.statuses = statuses;
	}

	@Override
	public FeeStatus statusFor(String studentUniqueId) {
		return statuses.statusFor(studentUniqueId);
	}

	@Override
	public Map<String, FeeStatus> statusesFor(Collection<String> studentUniqueIds) {
		return statuses.statusesFor(studentUniqueIds);
	}
}

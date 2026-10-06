package com.school.fees.app;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.school.common.fees.FeeStatus;
import com.school.fees.domain.Invoice;
import com.school.fees.domain.InvoiceStatus;
import com.school.fees.domain.LateFineRule;
import com.school.fees.infra.InvoiceRepository;
import org.springframework.stereotype.Component;

/**
 * Derives a student's fee status from their invoices, and nothing else.
 *
 * <p>Split out from {@link InvoiceService} for one concrete reason: this is the only part of the fees
 * module that <strong>people</strong> consumes, and it must not drag the rest in behind it.
 * {@code StudentService} needs a status for every row of the student list, so it depends on
 * {@link StudentFeeStatusService} → this class. Were the status computed by {@code InvoiceService},
 * that chain would continue into {@code ConcessionService}, which needs {@code StudentService} to
 * validate a student — a constructor cycle Spring cannot resolve, and one Spring Modulith does not
 * catch, because every package-level edge in it is perfectly legal.
 *
 * <p>So the rule here is the narrow dependency set: invoices, the structures' late-fine rules, and a
 * clock. Nothing that knows about students or concessions.
 */
@Component
public class FeeStatusReader {

	private final InvoiceRepository invoices;
	private final FeeStructureService structures;
	private final Clock clock;

	public FeeStatusReader(InvoiceRepository invoices, FeeStructureService structures, Clock clock) {
		this.invoices = invoices;
		this.structures = structures;
		this.clock = clock;
	}

	/** The status of one student. */
	public FeeStatus statusFor(String studentUniqueId) {
		List<Invoice> ledger = invoices.findByStudentUniqueIdOrderByDueDateAsc(studentUniqueId);
		return statusOf(ledger, structures.lateFineRulesOf(structureIdsOf(ledger)), today());
	}

	/**
	 * The same for many students in one read, which is what keeps the {@code feeStatus} column on the
	 * student list to two queries rather than two per row.
	 *
	 * <p>Every id asked for is present in the result. A student with no invoices is
	 * {@link FeeStatus#PAID}: they have no balance, which is what PAID means.
	 */
	public Map<String, FeeStatus> statusesFor(Collection<String> studentUniqueIds) {
		if (studentUniqueIds == null || studentUniqueIds.isEmpty()) {
			return Map.of();
		}
		List<Invoice> all = invoices.findByStudentUniqueIdIn(studentUniqueIds);
		Map<String, LateFineRule> rules = structures.lateFineRulesOf(structureIdsOf(all));
		LocalDate today = today();
		Map<String, List<Invoice>> byStudent = all.stream()
				.collect(Collectors.groupingBy(Invoice::getStudentUniqueId));

		Map<String, FeeStatus> statuses = new LinkedHashMap<>();
		studentUniqueIds.forEach(uniqueId ->
				statuses.put(uniqueId, statusOf(byStudent.getOrDefault(uniqueId, List.of()), rules, today)));
		return statuses;
	}

	/**
	 * The status rules, in the order they are tested. Nothing outstanding is PAID — including a
	 * student with no invoices at all. Otherwise OVERDUE wins over PARTIAL: a part-paid invoice that
	 * is past its grace period is still late, and late is the actionable answer.
	 *
	 * <p>Public and parameterised so a caller that has already loaded a ledger — the student fee
	 * statement — can have the status without a second round of queries.
	 */
	public FeeStatus statusOf(List<Invoice> ledger, Map<String, LateFineRule> rules, LocalDate today) {
		long billed = 0L;
		long paid = 0L;
		boolean overdue = false;
		for (Invoice invoice : ledger) {
			if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
				continue;
			}
			billed += invoice.getNetAmount();
			paid += invoice.getPaidAmount();
			overdue = overdue || LateFine.isOverdue(invoice, rules.get(invoice.getStructureId()), today);
		}
		if (billed - paid <= 0L) {
			return FeeStatus.PAID;
		}
		if (overdue) {
			return FeeStatus.OVERDUE;
		}
		return paid > 0L ? FeeStatus.PARTIAL : FeeStatus.DUE;
	}

	/** Today in the school's own timezone, which is the clock's. */
	public LocalDate today() {
		return LocalDate.now(clock);
	}

	/** The structures behind a ledger, for looking their late-fine rules up in one read. */
	public static Set<String> structureIdsOf(List<Invoice> ledger) {
		return ledger.stream()
				.map(Invoice::getStructureId)
				.filter(Objects::nonNull)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}
}

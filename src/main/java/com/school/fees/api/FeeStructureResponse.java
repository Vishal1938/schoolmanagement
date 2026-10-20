package com.school.fees.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.school.fees.domain.FeeStructure;
import com.school.fees.domain.InstallmentKind;
import com.school.fees.domain.LateFineRule;

/**
 * One fee structure, with head names resolved for display and every total pre-computed so the
 * frontend never adds money up itself.
 *
 * <p>Installments come back in the order the register reads them: the recurring ones — months and
 * semesters together — in period order first, then the one-off charges by due date. The stored order
 * is whatever each endpoint happened to write in, which is not something a client should have to sort
 * out.
 *
 * <p>All amounts are paise.
 */
public record FeeStructureResponse(
		String id,
		String sessionId,
		String classId,
		String className,
		List<Installment> installments,
		LateFineRule lateFine,
		long yearTotal,
		Instant createdAt,
		Instant updatedAt) {

	/**
	 * @param kind        MONTHLY, SEMESTER or ONE_TIME; always present, because an installment stored
	 *                    without one is reported as the MONTHLY it is taken to be
	 * @param label       what to head a column with — the name unless a different label was set
	 * @param periodStart first day of the session this covers, {@code periodEnd} the last, inclusive.
	 *                    Both null on a ONE_TIME fee, which covers no period; a MONTHLY one with none
	 *                    stored reports the calendar month of its due date
	 * @param total       the sum of the items for a section with no overrides
	 */
	public record Installment(String name, String label, InstallmentKind kind, LocalDate dueDate,
			LocalDate periodStart, LocalDate periodEnd, List<Item> items, long total) {
	}

	/**
	 * @param sectionAmounts the overrides, or null when every section pays {@code amount}
	 */
	public record Item(String headId, String headName, long amount, Map<String, Long> sectionAmounts) {
	}

	/**
	 * Recurring installments first — months and semesters interleaved in period order — then the
	 * one-off charges by due date. A null date, which the stored shape allows, sorts last rather than
	 * failing the comparison.
	 */
	private static final Comparator<Installment> REGISTER_ORDER = Comparator
			.comparingInt((Installment installment) -> installment.kind().isRecurring() ? 0 : 1)
			.thenComparing(FeeStructureResponse::orderingDateOf, Comparator.nullsLast(Comparator.naturalOrder()))
			.thenComparing(Installment::name);

	/** The start of the period if it covers one, otherwise the due date. */
	private static LocalDate orderingDateOf(Installment installment) {
		return installment.periodStart() == null ? installment.dueDate() : installment.periodStart();
	}

	/**
	 * @param headNames head id to name; a head that has since vanished shows as null rather than
	 *                  failing the read
	 */
	public static FeeStructureResponse of(FeeStructure structure, String className,
			Map<String, String> headNames) {
		List<Installment> installments = structure.installmentsOrEmpty().stream()
				.map(installment -> new Installment(
						installment.name(),
						installment.displayLabel(),
						installment.resolvedKind(),
						installment.dueDate(),
						installment.resolvedPeriodStart(),
						installment.resolvedPeriodEnd(),
						installment.items() == null ? List.<Item>of() : installment.items().stream()
								.map(item -> new Item(item.headId(), headNames.get(item.headId()), item.amount(),
										item.sectionAmounts()))
								.toList(),
						installment.total()))
				.sorted(REGISTER_ORDER)
				.toList();
		return new FeeStructureResponse(structure.getId(), structure.getSessionId(), structure.getClassId(),
				className, installments, structure.getLateFine(), structure.yearTotal(), structure.getCreatedAt(),
				structure.getUpdatedAt());
	}
}

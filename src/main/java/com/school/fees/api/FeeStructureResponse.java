package com.school.fees.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.school.fees.domain.FeeStructure;
import com.school.fees.domain.LateFineRule;

/**
 * One fee structure, with head names resolved for display and every total pre-computed so the
 * frontend never adds money up itself.
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

	public record Installment(String name, LocalDate dueDate, List<Item> items, long total) {
	}

	public record Item(String headId, String headName, long amount) {
	}

	/**
	 * @param headNames head id to name; a head that has since vanished shows as null rather than
	 *                  failing the read
	 */
	public static FeeStructureResponse of(FeeStructure structure, String className,
			Map<String, String> headNames) {
		List<Installment> installments = structure.getInstallments() == null ? List.of()
				: structure.getInstallments().stream()
						.map(installment -> new Installment(
								installment.name(),
								installment.dueDate(),
								installment.items() == null ? List.<Item>of() : installment.items().stream()
										.map(item -> new Item(item.headId(), headNames.get(item.headId()), item.amount()))
										.toList(),
								installment.total()))
						.toList();
		return new FeeStructureResponse(structure.getId(), structure.getSessionId(), structure.getClassId(),
				className, installments, structure.getLateFine(), structure.yearTotal(), structure.getCreatedAt(),
				structure.getUpdatedAt());
	}
}

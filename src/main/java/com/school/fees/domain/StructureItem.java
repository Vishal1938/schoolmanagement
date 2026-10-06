package com.school.fees.domain;

/**
 * One head and what it costs in one installment of a structure.
 *
 * @param amount paise, never a {@code double} (CLAUDE.md rule 3)
 */
public record StructureItem(String headId, long amount) {
}

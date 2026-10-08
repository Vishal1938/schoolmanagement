package com.school.people.api;

/**
 * The answer to {@code POST /students/promote}: what the run actually did, across every mapping.
 *
 * @param promoted  moved up into their mapping's target class in the new session
 * @param heldBack  kept in the same class, but moved into the new session — the students named in
 *                  {@code excludeUniqueIds}
 * @param graduated students of a class with no target: marked {@code ALUMNI}, with their final
 *                  enrollment left exactly as it was
 */
public record PromotionResult(int promoted, int heldBack, int graduated) {
}

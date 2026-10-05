package com.school.schoolconfig.domain;

/**
 * One "why us" card on the landing page.
 *
 * @param title       the headline of the card
 * @param description the supporting line
 * @param icon        a Tabler icon name the frontend resolves to a glyph, e.g. {@code school}. Stored
 *                    as a plain string so adding an icon never needs a backend change.
 */
public record Highlight(String title, String description, String icon) {
}

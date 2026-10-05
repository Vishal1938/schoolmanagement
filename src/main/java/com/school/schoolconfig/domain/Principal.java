package com.school.schoolconfig.domain;

/**
 * The head of the school, as introduced on the landing page. Optional as a whole, and every field
 * inside it is optional too: a school that has not filled this in simply has no principal section.
 *
 * @param name        the principal's name
 * @param designation title to print under the name, e.g. {@code Principal} or {@code Director}
 * @param photoUrl    portrait, stored under the public prefix
 * @param message     the principal's message to visitors
 */
public record Principal(String name, String designation, String photoUrl, String message) {
}

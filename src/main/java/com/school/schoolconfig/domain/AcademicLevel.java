package com.school.schoolconfig.domain;

/**
 * One stage of schooling as advertised on the landing page, e.g. "Primary, classes I-V".
 *
 * <p>Purely descriptive: the real class and section records live in the academics module (B4). A
 * school can describe its stages however it likes without those two having to agree.
 *
 * @param name        what the school calls the stage, e.g. {@code Primary}
 * @param range       the classes it covers, as free text, e.g. {@code Classes I-V}
 * @param description a sentence or two about it
 */
public record AcademicLevel(String name, String range, String description) {
}

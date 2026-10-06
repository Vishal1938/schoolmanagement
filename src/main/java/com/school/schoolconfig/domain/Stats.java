package com.school.schoolconfig.domain;

/**
 * The counters shown on the landing page. These are edited by the admin rather than computed, so a
 * school can publish rounded figures; the dashboards in B16 report the real numbers.
 */
public record Stats(int students, int teachers, int years, int passPercentage) {
}

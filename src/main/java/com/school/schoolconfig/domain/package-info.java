/**
 * Documents and value types for the schoolconfig module.
 *
 * <p>Exposed as a named interface because other modules legitimately handle these read-only values —
 * B8 needs the working days, B9 the grade bands, B12 the receipt prefix. Only immutable value types
 * and enums belong here; the repository stays in {@code infra}, so no module can write configuration
 * behind {@code SchoolConfigService}'s back.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.school.schoolconfig.domain;

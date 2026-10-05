/**
 * Documents and enums for the academics module.
 *
 * <p>Exposed as a named interface because the services above hand these out read-only: B5 resolves a
 * {@code SchoolClass} to validate an enrollment, and anything dated carries the {@code AcademicSession}
 * the {@code AcademicContext} returned. The repositories stay in {@code infra}, so no other module
 * can write a class or a session behind the services' back.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.school.academics.domain;

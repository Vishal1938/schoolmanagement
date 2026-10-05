/**
 * Application services (the module's public entry point) for the academics module.
 *
 * <p>Exposed as a Spring Modulith named interface. Nearly every other module needs the academic
 * structure — {@code AcademicContext} for the current session, {@code SchoolClassService} to resolve
 * a class and check that a section exists — and they all come through here rather than through this
 * module's repositories.
 */
@org.springframework.modulith.NamedInterface("app")
package com.school.academics.app;

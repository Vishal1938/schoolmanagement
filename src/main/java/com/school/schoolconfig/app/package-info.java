/**
 * Services for the schoolconfig module.
 *
 * <p>Exposed as a Spring Modulith named interface, because this is how the rest of the application is
 * allowed to reach school configuration: {@code SchoolConfigService}, never
 * {@code SchoolConfigRepository}.
 */
@org.springframework.modulith.NamedInterface("app")
package com.school.schoolconfig.app;

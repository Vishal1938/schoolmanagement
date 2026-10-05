/**
 * Application services (the module's public entry point) for the people module.
 *
 * <p>Exposed as a Spring Modulith named interface. Attendance, exams, fees and payroll all need to
 * know who is on the rolls, and they ask here.
 *
 * <p>What leaves this module is deliberately thin: {@code StudentRef} and {@code EmployeeRef} carry
 * an id, a name and the one field the caller sorts or groups by. The documents themselves stay
 * behind the services, and so do the per-role projections — no other module should be in a position
 * to hand out a guardian's phone number or a bank account.
 */
@org.springframework.modulith.NamedInterface("app")
package com.school.people.app;

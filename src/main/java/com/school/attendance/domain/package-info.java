/**
 * Documents and enums for the attendance module.
 *
 * <p>Exposed as a named interface for two types only:
 * {@link com.school.attendance.domain.AttendanceStatus} and
 * {@link com.school.attendance.domain.EmployeeAttendanceEntry}, which together are how a register is
 * handed to {@code EmployeeAttendanceService} — B20's demonstration data seeder marks months of past
 * staff attendance through it. Sharing the enum is also what stops a second spelling of
 * {@code HALF_DAY} appearing, the same arrangement as {@code fees.domain} and its
 * {@code InstallmentKind}.
 *
 * <p>The registers themselves stay behind the services, and the repositories stay in {@code infra}.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.school.attendance.domain;

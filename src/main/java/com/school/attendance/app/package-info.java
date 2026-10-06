/**
 * Application services (the module's public entry point) for the attendance module.
 *
 * <p>Exposed as a Spring Modulith named interface for one narrow reason: the B9 report card prints
 * the student's attendance for the session, and it asks
 * {@code StudentAttendanceService.shareFor(...)} for an {@link com.school.attendance.app.AttendanceShare}
 * — a percentage and the days it was worked out from. B13 asks
 * {@code EmployeeAttendanceService.unpaidDays(...)} for the two counts a prorated payroll run docks.
 * The registers themselves, and the per-day record, stay behind the services.
 */
@org.springframework.modulith.NamedInterface("app")
package com.school.attendance.app;

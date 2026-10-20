/**
 * Documents and enums for the payroll module.
 *
 * <p>Exposed as a named interface for the value types that make up a salary structure —
 * {@link com.school.payroll.domain.SalaryComponent}, {@link com.school.payroll.domain.DeductionRule},
 * {@link com.school.payroll.domain.DeductionType} — and for
 * {@link com.school.payroll.domain.SalaryPaymentMode}. Those are the parameter types of
 * {@code SalaryStructureService} and {@code PayrollService}, so a module that drives them, as B20's
 * demonstration data seeder does, has to be able to name them.
 *
 * <p>Nothing else here is meant for other modules. {@code PayrollRecord} and {@code SalaryStructure}
 * are what somebody is paid, and the repositories stay in {@code infra}, so reading a salary still
 * goes through the services in {@code app} and their authorization checks.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.school.payroll.domain;

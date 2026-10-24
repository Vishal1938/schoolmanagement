/**
 * Application services (the module's public entry point) for the accounts module.
 *
 * <p>This module depends on payment and payroll and never the other way round: it listens for
 * {@code PaymentCapturedEvent}, {@code PayrollRecordPaidEvent} and the two advance events, and reads
 * their history through those modules' own services for the one-time backfill. Nothing in fees,
 * payment or payroll knows the books exist.
 */
@org.springframework.modulith.NamedInterface("app")
package com.school.accounts.app;

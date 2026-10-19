/**
 * Application services (the module's public entry point) for the payment module.
 *
 * <p>This module depends on fees and never the other way round: invoices are written only through
 * {@code InvoiceService.allocate}, and the collection report — which reports payments rather than
 * invoices — lives here even though it is served under {@code /fees/reports/}. That is what keeps
 * the two modules from depending on each other.
 *
 * <p>Exposed as a named interface for B16, whose admin dashboard reads the day's takings and the
 * most recent receipts from here.
 */
@org.springframework.modulith.NamedInterface("app")
package com.school.payment.app;

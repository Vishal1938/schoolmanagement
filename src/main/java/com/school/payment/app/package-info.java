/**
 * Application services (the module's public entry point) for the payment module.
 *
 * <p>This module depends on fees and never the other way round: invoices are written only through
 * {@code InvoiceService.allocate}, and the collection report — which reports payments rather than
 * invoices — lives here even though it is served under {@code /fees/reports/}. That is what keeps
 * the two modules from depending on each other.
 */
package com.school.payment.app;

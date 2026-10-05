/**
 * Application services (the module's public entry point) for the fees module.
 *
 * <p>Exposed as a named interface for B12, which records payments against the invoices raised here.
 *
 * <p>The fee <em>status</em> other modules consume is not in this package: it is
 * {@link com.school.common.fees.StudentFeeStatusProvider}, implemented here by
 * {@link com.school.fees.app.StudentFeeStatusService}. That inversion is what keeps the module graph
 * acyclic now that fees needs {@code people} for the class roster — see that package's javadoc.
 */
@org.springframework.modulith.NamedInterface("app")
package com.school.fees.app;

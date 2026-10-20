/**
 * Documents and enums for the payment module.
 *
 * <p>Exposed as a named interface for two types only: {@link com.school.payment.domain.PaymentMode}
 * and {@link com.school.payment.domain.PayerRelation}. They are the parameter types of
 * {@code PaymentService}'s take-money methods, so a module that drives those — B20's demonstration
 * data seeder — has to be able to name them. Sharing the enums is also what keeps two spellings of
 * {@code UPI_OFFLINE} from drifting apart, the same arrangement as {@code fees.domain} and its
 * {@code InstallmentKind}.
 *
 * <p>Nothing else here is meant for other modules. {@code Payment} carries amounts, a payer's name and
 * a receipt number, and the repository stays in {@code infra} — so the only way to the money is still
 * through the services in {@code app}.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.school.payment.domain;

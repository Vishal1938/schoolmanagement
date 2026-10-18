/**
 * Documents and enums for the fees module.
 *
 * <p>Exposed as a named interface for one type only:
 * {@link com.school.fees.domain.InstallmentKind}. The collection report in B12 belongs to the payment
 * module, and filtering it by kind means the two modules have to agree on what {@code MONTHLY} is
 * spelled like — which they do by sharing the enum rather than by letting two copies drift apart. The
 * same arrangement, and the same reason, as {@code quiz.domain} and its {@code QuestionType}.
 *
 * <p>Nothing else here is meant for other modules. {@code Invoice}, {@code FeeStructure} and
 * {@code Concession} carry amounts a teacher must never see (CLAUDE.md rule 2), and the repositories
 * stay in {@code infra}, so the only way to the money is still through the services in {@code app}.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.school.fees.domain;

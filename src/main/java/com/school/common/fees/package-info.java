/**
 * The fee-status contract: the one thing about a student's fees that other modules may learn.
 *
 * <p>It lives in {@code common} rather than in {@code fees} to keep the module graph acyclic.
 * {@code fees} needs {@code people} — generating invoices needs the roster of a class, and reading a
 * ledger needs the student's name — while {@code people} needs only a status chip for its student
 * projections. Spring Modulith rejects a cycle between two modules, so the narrower of the two
 * contracts is inverted: {@code fees} implements {@link com.school.common.fees.StudentFeeStatusProvider}
 * and {@code people} consumes it, and neither imports the other.
 *
 * <p>This stays deliberately tiny. A status and nothing else crosses it — no invoice, no amount, no
 * payment history — which is what lets the teacher projection carry a fee status without being able
 * to carry a figure (CLAUDE.md rule 2).
 */
package com.school.common.fees;

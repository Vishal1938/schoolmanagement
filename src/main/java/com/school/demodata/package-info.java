/**
 * The one-time demonstration data set: a whole college year of fees, payments, payroll and staff
 * attendance, written in one call and wiped in another.
 *
 * <p><strong>It owns no collection.</strong> Everything it creates belongs to another module, and it
 * creates all of it through that module's public services in {@code app} — the same services the
 * endpoints call. That is the point: the data it leaves behind is consistent because it was produced
 * by the real code paths, with the real validation, the real audit entries and the real receipt
 * numbering. There is no fixture loader here and no document anywhere in this package.
 *
 * <p>Nothing depends on this module, and it is only a module at all so Spring Modulith can check that
 * it reaches the rest of the application the legitimate way round.
 */
package com.school.demodata;

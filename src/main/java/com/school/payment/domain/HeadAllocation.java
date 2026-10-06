package com.school.payment.domain;

/**
 * One head's share of the principal collected on one invoice, in paise.
 *
 * <p>Stored rather than derived, so the "by head" line of the collection report is a sum of figures
 * fixed when the money was taken. Re-deriving it from the invoice would make last month's report
 * change when somebody corrects a fee structure.
 */
public record HeadAllocation(String headId, String headName, long amount) {
}

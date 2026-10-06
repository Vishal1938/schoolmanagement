package com.school.fees.domain;

/**
 * One head charged on one invoice.
 *
 * <p>{@code headName} is copied in rather than resolved on read: an invoice is a statement of what
 * was charged, and renaming a head two years later must not change what a receipt already issued
 * says it was for.
 *
 * @param amount paise
 */
public record InvoiceItem(String headId, String headName, long amount) {
}

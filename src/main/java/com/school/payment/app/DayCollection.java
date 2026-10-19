package com.school.payment.app;

import java.time.LocalDate;

/**
 * One day's takings: how much arrived and in how many payments.
 *
 * @param amount in paise, every mode together, late fines included
 * @param count  payments captured that day, so a reader can tell one large cheque from forty
 *               small UPI collections
 */
public record DayCollection(LocalDate date, long amount, int count) {
}

package com.school.payment.api;

import java.time.LocalDate;
import java.util.List;

import com.school.payment.domain.PaymentMode;

/**
 * {@code GET /fees/reports/collection}: what came in over a date range. All amounts are paise.
 *
 * <p>{@code totalCollected} is always {@code principalCollected + lateFineCollected}, and both
 * {@code byMode} and {@code daily} sum to it. {@code byHead} sums to {@code principalCollected}
 * only — a late fine belongs to no head, which is exactly why it is reported on its own.
 *
 * @param from  inclusive
 * @param to    inclusive
 * @param daily one entry per day that had a payment; days with nothing are left out rather than
 *              padded with zeros
 */
public record CollectionReportResponse(
		LocalDate from,
		LocalDate to,
		long totalCollected,
		long principalCollected,
		long lateFineCollected,
		int paymentCount,
		List<ModeTotal> byMode,
		List<HeadTotal> byHead,
		List<DayTotal> daily) {

	/** Totals for one payment mode, highest first. */
	public record ModeTotal(PaymentMode mode, long amount, int paymentCount) {
	}

	/** Totals for one fee head, highest first. Late fines are not here — they belong to no head. */
	public record HeadTotal(String headId, String headName, long amount) {
	}

	/** One day of the series, oldest first. */
	public record DayTotal(LocalDate date, long amount, int paymentCount) {
	}
}

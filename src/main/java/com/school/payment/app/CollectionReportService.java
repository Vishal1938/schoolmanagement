package com.school.payment.app;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.school.common.exceptions.FieldViolation;
import com.school.common.exceptions.ValidationException;
import com.school.fees.app.InvoiceService;
import com.school.fees.domain.InstallmentKind;
import com.school.payment.api.CollectionReportResponse;
import com.school.payment.domain.HeadAllocation;
import com.school.payment.domain.Payment;
import com.school.payment.domain.PaymentAllocation;
import com.school.payment.domain.PaymentMode;
import com.school.payment.domain.PaymentStatus;
import com.school.payment.infra.PaymentRepository;
import org.springframework.stereotype.Service;

/**
 * The collection report: what came in, by mode, by head and by day.
 *
 * <p>Every figure is a sum of numbers stored on the payments themselves, fixed at the moment each
 * payment was taken. Nothing is re-derived from the invoices, so last month's report says the same
 * thing next month even if somebody corrects a fee structure in between — which is the whole point
 * of a collection report.
 *
 * <p>This lives in the payment module, not in fees, because it reports payments. It is still served
 * at {@code /fees/reports/collection} so the reports read as one group to a client; fees already
 * serves the defaulters list, and keeping this here is what stops the two modules depending on each
 * other.
 */
@Service
public class CollectionReportService {

	/** A school with a long history still only has a few thousand payments a year. */
	private static final int MAX_RANGE_DAYS = 400;

	private final PaymentRepository payments;
	private final InvoiceService invoices;
	private final Clock clock;

	public CollectionReportService(PaymentRepository payments, InvoiceService invoices, Clock clock) {
		this.payments = payments;
		this.invoices = invoices;
		this.clock = clock;
	}

	/**
	 * Collections between two dates, both inclusive.
	 *
	 * <p>Dates are resolved in the school's own zone (the injected clock's), so "today's collection"
	 * means the school's day rather than UTC's — the difference is five and a half hours of takings in
	 * India.
	 *
	 * @param from defaults to the first of the current month
	 * @param to   defaults to today
	 * @param kind optional; report only what was collected against invoices of this kind, e.g. MONTHLY
	 *             to answer "how much of the monthly fees came in this month" separately from the
	 *             admission fees and the fines. Omit for everything, which is the unchanged behaviour
	 */
	public CollectionReportResponse collection(LocalDate from, LocalDate to, InstallmentKind kind) {
		LocalDate today = LocalDate.now(clock);
		LocalDate start = from == null ? today.withDayOfMonth(1) : from;
		LocalDate end = to == null ? today : to;
		requireUsableRange(start, end);

		// Half-open on the instant axis: the day after `end` at midnight, so the whole of `end` counts.
		Instant fromInstant = start.atStartOfDay(clock.getZone()).toInstant();
		Instant toInstant = end.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
		List<Payment> captured = payments.findCapturedInRange(
				PaymentStatus.CAPTURED, fromInstant, toInstant);
		// One lookup for the whole range rather than per payment. Empty when no kind was asked for, in
		// which case nothing below consults it.
		Map<String, InstallmentKind> kinds = kind == null ? Map.of() : invoices.kindsOf(invoiceIdsOf(captured));

		long total = 0L;
		long principal = 0L;
		long lateFine = 0L;
		int paymentCount = 0;
		Map<PaymentMode, long[]> byMode = new LinkedHashMap<>();
		Map<String, HeadTally> byHead = new LinkedHashMap<>();
		Map<LocalDate, long[]> daily = new LinkedHashMap<>();

		for (Payment payment : captured) {
			List<PaymentAllocation> counted = kind == null
					? allocationsOf(payment)
					: allocationsOf(payment).stream()
							.filter(allocation -> kinds.get(allocation.invoiceId()) == kind)
							.toList();
			// A payment that touched none of the kind asked for is not part of this report at all — it
			// would otherwise inflate paymentCount with rows contributing nothing.
			if (kind != null && counted.isEmpty()) {
				continue;
			}

			// Unfiltered, the payment's own figures: `amount` is what was received, which can exceed what
			// was allocated (see PAYMENT_UNALLOCATED), and a report of takings must say what was taken.
			// Filtered, there is no such figure to quote, so the allocations are summed instead.
			long paymentTotal = kind == null ? payment.getAmount() : principalOf(counted) + lateFineOf(counted);
			total += paymentTotal;
			principal += kind == null ? payment.principalTotal() : principalOf(counted);
			lateFine += kind == null ? payment.lateFineTotal() : lateFineOf(counted);
			paymentCount++;

			// long[] {amount, count} rather than a mutable holder class: two running numbers per key.
			long[] mode = byMode.computeIfAbsent(payment.getMode(), key -> new long[2]);
			mode[0] += paymentTotal;
			mode[1]++;

			LocalDate day = LocalDate.ofInstant(payment.getPaidAt(), clock.getZone());
			long[] dayTotals = daily.computeIfAbsent(day, key -> new long[2]);
			dayTotals[0] += paymentTotal;
			dayTotals[1]++;

			for (PaymentAllocation allocation : counted) {
				for (HeadAllocation head : headsOf(allocation)) {
					byHead.computeIfAbsent(head.headId(), key -> new HeadTally(head.headName())).amount +=
							head.amount();
				}
			}
		}

		return new CollectionReportResponse(start, end, kind, total, principal, lateFine, paymentCount,
				modeTotals(byMode), headTotals(byHead), dayTotals(daily));
	}

	/**
	 * What came in on one day, and how many payments it took — the dashboard's "today's collection"
	 * (B16).
	 *
	 * <p>Not {@link #collection} narrowed to a single day, though it would give the same two numbers:
	 * that one also builds the by-mode, by-head and daily breakdowns, none of which a two-figure tile
	 * shows. The date is resolved in the school's own zone, so "today" ends at midnight locally rather
	 * than five and a half hours early.
	 *
	 * <p>{@code amount} is what was received, including any late fine and any part of a payment no
	 * invoice needed: a report of takings says what was taken.
	 */
	public DayCollection dayTotal(LocalDate date) {
		Instant from = date.atStartOfDay(clock.getZone()).toInstant();
		Instant to = date.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
		List<Payment> captured = payments.findCapturedInRange(PaymentStatus.CAPTURED, from, to);
		return new DayCollection(date, captured.stream().mapToLong(Payment::getAmount).sum(), captured.size());
	}

	/** Every invoice any of these payments touched, for one kind lookup across the range. */
	private static Set<String> invoiceIdsOf(List<Payment> captured) {
		return captured.stream()
				.flatMap(payment -> allocationsOf(payment).stream())
				.map(PaymentAllocation::invoiceId)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static long principalOf(List<PaymentAllocation> allocations) {
		return allocations.stream().mapToLong(PaymentAllocation::amount).sum();
	}

	private static long lateFineOf(List<PaymentAllocation> allocations) {
		return allocations.stream().mapToLong(PaymentAllocation::lateFine).sum();
	}

	private void requireUsableRange(LocalDate from, LocalDate to) {
		if (to.isBefore(from)) {
			throw new ValidationException("That date range runs backwards",
					List.of(new FieldViolation("to", "must not be before from")));
		}
		if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
			throw new ValidationException("That date range is too wide",
					List.of(new FieldViolation("to", "must be within " + MAX_RANGE_DAYS + " days of from")));
		}
	}

	/** Highest first: the report is read to see where the money came from. */
	private static List<CollectionReportResponse.ModeTotal> modeTotals(Map<PaymentMode, long[]> byMode) {
		List<CollectionReportResponse.ModeTotal> totals = new ArrayList<>(byMode.size());
		byMode.forEach((mode, figures) ->
				totals.add(new CollectionReportResponse.ModeTotal(mode, figures[0], (int) figures[1])));
		totals.sort(Comparator.comparingLong(CollectionReportResponse.ModeTotal::amount).reversed());
		return totals;
	}

	private static List<CollectionReportResponse.HeadTotal> headTotals(Map<String, HeadTally> byHead) {
		List<CollectionReportResponse.HeadTotal> totals = new ArrayList<>(byHead.size());
		byHead.forEach((headId, tally) ->
				totals.add(new CollectionReportResponse.HeadTotal(headId, tally.headName, tally.amount)));
		totals.sort(Comparator.comparingLong(CollectionReportResponse.HeadTotal::amount).reversed());
		return totals;
	}

	/** Oldest first: this one is a series, so it is read left to right. */
	private static List<CollectionReportResponse.DayTotal> dayTotals(Map<LocalDate, long[]> daily) {
		List<CollectionReportResponse.DayTotal> totals = new ArrayList<>(daily.size());
		daily.forEach((date, figures) ->
				totals.add(new CollectionReportResponse.DayTotal(date, figures[0], (int) figures[1])));
		totals.sort(Comparator.comparing(CollectionReportResponse.DayTotal::date));
		return totals;
	}

	private static List<PaymentAllocation> allocationsOf(Payment payment) {
		return payment.getAllocations() == null ? List.of() : payment.getAllocations();
	}

	private static List<HeadAllocation> headsOf(PaymentAllocation allocation) {
		return allocation.heads() == null ? List.of() : allocation.heads();
	}

	/** The head's name is carried alongside its running total so the report needs no lookup. */
	private static final class HeadTally {

		private final String headName;
		private long amount;

		private HeadTally(String headName) {
			this.headName = headName;
		}
	}
}

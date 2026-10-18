package com.school.fees.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.school.fees.domain.InstallmentKind;

/**
 * {@code GET /fees/register}: the fee collection register for one class, as a grid.
 *
 * <p>This is the sheet the office actually works from — a row per student, a column per month and
 * then per one-off fee, a cell saying whether that month is in. It is assembled here rather than in
 * the frontend because the cells have to line up with the columns exactly, and because "which invoice
 * is this cell" is the thing a clerk clicks to take the money.
 *
 * <p>All amounts are paise. <strong>Late fines are deliberately not in any figure here.</strong> A
 * register is arithmetic a clerk checks by eye, and {@code due - paid = balance} has to hold in every
 * row and column; a running fine that grows daily would break that and make two readings of the same
 * sheet disagree. The fine is still charged — {@code POST /fees/register/mark-paid} and the student
 * ledger both include it, computed at the moment of payment — it simply is not a column.
 *
 * @param section null when the register covers every section of the class
 * @param columns the grid's columns in display order: the recurring months by due date, then the
 *                one-off fees by due date. {@code key} is what {@link Row#cells()} is keyed by
 */
public record FeeRegisterResponse(
		String sessionId,
		String classId,
		String className,
		String section,
		List<Column> columns,
		List<Row> rows,
		Footer footer) {

	/**
	 * @param key   the installment name, which is also the key into every row's {@code cells}
	 * @param label what to print as the column heading
	 */
	public record Column(String key, String label, InstallmentKind kind, LocalDate dueDate) {
	}

	/**
	 * One student's line.
	 *
	 * @param cells        column key to the cell, with <strong>no entry at all</strong> for a column
	 *                     that student has no invoice for. That is a real state and not the same as an
	 *                     unpaid one — a student admitted in November has no July invoice — so the key
	 *                     is absent rather than carrying a zero
	 * @param otherCharges the individual charges on this student, which have no column because they are
	 *                     nobody else's
	 * @param totals       across this row: the columns and the other charges together
	 */
	public record Row(
			String uniqueId,
			String name,
			int rollNo,
			Map<String, Cell> cells,
			OtherCharges otherCharges,
			Totals totals) {
	}

	/**
	 * @param amount  what was billed, after any concession
	 * @param balance what is left of it
	 */
	public record Cell(String invoiceId, long amount, long balance, CellStatus status) {
	}

	/**
	 * The CUSTOM invoices on one student, summarised and then listed.
	 *
	 * @param count        how many are still unsettled plus however many are settled — every charge on
	 *                     the student for this session, so the count matches the list
	 * @param totalBalance what is still owed across them
	 */
	public record OtherCharges(int count, long totalBalance, List<Item> items) {
	}

	/** One individual charge, with the note the office typed when raising it. */
	public record Item(
			String invoiceId,
			String label,
			String note,
			LocalDate dueDate,
			long amount,
			long balance,
			CellStatus status) {
	}

	/** {@code balance} is always {@code due - paid}; see the note on late fines above. */
	public record Totals(long due, long paid, long balance) {
	}

	/**
	 * The bottom of the sheet.
	 *
	 * @param columns      one entry per column, in the same order as {@link #columns()}
	 * @param otherCharges the same two figures for the individual charges taken together
	 * @param grandTotals  everything in the grid, columns and charges alike
	 */
	public record Footer(List<ColumnTotal> columns, ColumnTotal otherCharges, Totals grandTotals) {
	}

	/**
	 * @param collected what has been taken for this column across the class
	 * @param pending   what is still owed for it
	 */
	public record ColumnTotal(String key, String label, long collected, long pending) {
	}

	/**
	 * How a cell reads on the sheet.
	 *
	 * <p>Not {@code InvoiceStatus}: that enum has a CANCELLED member and no OVERDUE one, because an
	 * invoice's stored status is a fact about money while this is a fact about money <em>and today's
	 * date</em>. Cancelled invoices are left out of the register entirely, as they are out of every
	 * other total.
	 */
	public enum CellStatus {

		/** Settled in full. */
		PAID,

		/** Something has been paid, a balance remains, and it is not yet past its due date. */
		PARTIAL,

		/** Nothing paid, not yet past its due date. */
		UNPAID,

		/** Past its due date with a balance outstanding, whether or not part of it has been paid. */
		OVERDUE
	}
}

package com.school.demodata.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.school.payment.domain.PaymentMode;

/**
 * What one seeding run produced, accumulated as it goes.
 *
 * <p>Mutable, which nothing else in this application is. It is handed down through the three seeders
 * and filled in by each, and the alternative — every step returning a record that the orchestrator
 * merges — would be a dozen types that exist only to be added up. It never leaves this module as
 * itself: the controller reads it into an immutable response.
 *
 * <p>The counts are of what was actually written. A step that was skipped with a warning — a student
 * who is not on the roll, a designation with no salary rule — does not count towards anything, and
 * the warning says why.
 */
public class DemoSummary {

	private int feeHeads;
	private int feeStructures;
	private int holidays;
	private int invoicesCreated;
	private int invoicesSkipped;
	private int individualCharges;
	private final Map<PaymentMode, ModeTotal> paymentsByMode = new LinkedHashMap<>();
	private int receiptsIssued;
	private long totalCollected;
	private int salaryStructures;
	private int salaryAdvances;
	private int attendanceDaysMarked;
	private int attendanceMarks;
	private final Map<String, PayrollMonth> payrollByMonth = new LinkedHashMap<>();
	private int studentsMarkedLeft;
	private int employeesMarkedLeft;
	private final List<String> warnings = new ArrayList<>();

	// --- recording --------------------------------------------------------------------------------

	void addFeeHead() {
		feeHeads++;
	}

	void addFeeStructure() {
		feeStructures++;
	}

	void addHoliday() {
		holidays++;
	}

	void addInvoices(int created, int skipped) {
		invoicesCreated += created;
		invoicesSkipped += skipped;
	}

	void addIndividualCharge() {
		individualCharges++;
	}

	/**
	 * One payment that landed, by the mode it arrived in.
	 *
	 * @param receiptNo the number issued, which a captured payment always has — counted separately so
	 *                  the summary can state "receipts issued" without assuming it equals the payments
	 */
	void addPayment(PaymentMode mode, long amount, String receiptNo) {
		paymentsByMode.merge(mode, new ModeTotal(1, amount),
				(first, second) -> new ModeTotal(first.count() + second.count(),
						first.amount() + second.amount()));
		totalCollected += amount;
		if (receiptNo != null) {
			receiptsIssued++;
		}
	}

	void addSalaryStructure() {
		salaryStructures++;
	}

	void addSalaryAdvance() {
		salaryAdvances++;
	}

	/** One day's staff register, with how many employees it marked. */
	void addAttendanceDay(int marks) {
		attendanceDaysMarked++;
		attendanceMarks += marks;
	}

	void addPayrollRun(String month, int created) {
		payrollByMonth.merge(month, new PayrollMonth(created, 0),
				(first, second) -> new PayrollMonth(first.created() + second.created(), first.paid()));
	}

	void addPayrollPaid(String month, int paid) {
		payrollByMonth.merge(month, new PayrollMonth(0, paid),
				(first, second) -> new PayrollMonth(first.created(), first.paid() + second.paid()));
	}

	void addStudentMarkedLeft() {
		studentsMarkedLeft++;
	}

	void addEmployeeMarkedLeft() {
		employeesMarkedLeft++;
	}

	/** Something named in the specification was not found, or was refused. Never a failure. */
	void warn(String warning) {
		warnings.add(warning);
	}

	// --- reading ----------------------------------------------------------------------------------

	public int feeHeads() {
		return feeHeads;
	}

	public int feeStructures() {
		return feeStructures;
	}

	public int holidays() {
		return holidays;
	}

	public int invoicesCreated() {
		return invoicesCreated;
	}

	public int invoicesSkipped() {
		return invoicesSkipped;
	}

	public int individualCharges() {
		return individualCharges;
	}

	public Map<PaymentMode, ModeTotal> paymentsByMode() {
		return Map.copyOf(paymentsByMode);
	}

	public int payments() {
		return paymentsByMode.values().stream().mapToInt(ModeTotal::count).sum();
	}

	public int receiptsIssued() {
		return receiptsIssued;
	}

	public long totalCollected() {
		return totalCollected;
	}

	public int salaryStructures() {
		return salaryStructures;
	}

	public int salaryAdvances() {
		return salaryAdvances;
	}

	public int attendanceDaysMarked() {
		return attendanceDaysMarked;
	}

	public int attendanceMarks() {
		return attendanceMarks;
	}

	public Map<String, PayrollMonth> payrollByMonth() {
		return Map.copyOf(payrollByMonth);
	}

	public int studentsMarkedLeft() {
		return studentsMarkedLeft;
	}

	public int employeesMarkedLeft() {
		return employeesMarkedLeft;
	}

	public List<String> warnings() {
		return List.copyOf(warnings);
	}

	/** The flat form that goes into the audit entry. */
	Map<String, Object> asAuditCounts() {
		Map<String, Object> counts = new LinkedHashMap<>();
		counts.put("feeHeads", feeHeads);
		counts.put("feeStructures", feeStructures);
		counts.put("holidays", holidays);
		counts.put("invoicesCreated", invoicesCreated);
		counts.put("individualCharges", individualCharges);
		counts.put("payments", payments());
		counts.put("receiptsIssued", receiptsIssued);
		counts.put("totalCollected", totalCollected);
		counts.put("salaryStructures", salaryStructures);
		counts.put("salaryAdvances", salaryAdvances);
		counts.put("attendanceDaysMarked", attendanceDaysMarked);
		counts.put("payrollRecords", payrollByMonth.values().stream().mapToInt(PayrollMonth::created).sum());
		counts.put("studentsMarkedLeft", studentsMarkedLeft);
		counts.put("employeesMarkedLeft", employeesMarkedLeft);
		counts.put("warnings", warnings.size());
		return counts;
	}

	/** How many payments came in one way, and what they came to in paise. */
	public record ModeTotal(int count, long amount) {
	}

	/**
	 * One month of payroll.
	 *
	 * @param created records the run wrote
	 * @param paid    how many of them were then paid out. October's is deliberately 0
	 */
	public record PayrollMonth(int created, int paid) {

		public int pending() {
			return Math.max(0, created - paid);
		}
	}
}
